package session;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import message.TFTPMessage;
import request.Request;
import status.ITFTPStatus;
import status.TFTPErrorStatus;
import status.TFTPOKStatus;



public class TFTPSession 
{

	private final int 		TFTP_DEFAULT_PORT 		=	69;
	private final int 		TFTP_DATA_BLOCK_LENGTH	= 	512;
	private final int		TFTP_PACKET_SIZE		=	516; // data is 512 bytes, then 2 bytes for opcode and 2 bytes for block number
	private final String 	CRYPTO_ALG				=	"SHA1PRNG";

	private DatagramSocket		_socket;

	private boolean 			_recieving;

	//local and server port number attributes.
	private int 				_localPortNumber;
	private int 				_serverPortNumber;

	//general attributes needed throughout the TFTP session
	private String				_hostname;
	private InetAddress			_inetAddress;
	private Request 			_requestType;
	private String				_foreignFilename;
	private String				_localFilename;
	private String 				_mode;


	//the blockNumber we are expecting
	private int 				_currentBlockNumber;

	//our DatagramPackets that will be used to send and recieve TFTP packets.
	private DatagramPacket 		_recievingPacket;
	private DatagramPacket 		_sendingPacket;

	//byte arrays backing our DatagramPackets
	private byte[]				_receivedPacketBuffer;
	private byte[]				_sendingPacketBuffer;

	//byte buffer used to write data blocks to a file
	private byte[]				_dataWriteBuffer;
	private byte[]				_dataReadBuffer;


	private File				_file;
	private FileOutputStream	_outputStream;




	//-------------------------------------------------------
	// CONSTRUCTOR
	//-------------------------------------------------------
	public TFTPSession(String hostname, String mode, String foreignFilename, String localFilename, Request request) throws SocketException, UnknownHostException, NoSuchAlgorithmException
	{

		_hostname = hostname;
		_foreignFilename = foreignFilename;
		_localFilename = localFilename;
		_mode = mode;
		
		_requestType = request;

		_currentBlockNumber = 1;

		_recieving = false;

		//Generate Random number between 0 and 65535 to be used as our port number
		//for the transfer session

		_inetAddress = InetAddress.getByName(hostname);

		SecureRandom rng = SecureRandom.getInstance(CRYPTO_ALG);
		_localPortNumber = rng.nextInt(65535);

		_socket = new DatagramSocket(_localPortNumber);

	}



	//-------------------------------------------------------
	// HEADER
	//-------------------------------------------------------
	private int readOpcode(byte[] packet)
	{

		int opcode = -1;

		ByteBuffer packetBuffer = ByteBuffer.wrap(packet);
		opcode = packetBuffer.getShort();

		return opcode;

	}


	private int readBlockNumber(byte[] packet)
	{

		int blockNumber = -1;

		ByteBuffer packetBuffer = ByteBuffer.wrap(packet);
		blockNumber = packetBuffer.getShort(2);

		return blockNumber;

	}



	//-------------------------------------------------------
	// REQUEST
	//-------------------------------------------------------
	private void writeRequest() throws IOException	//socket can be null, and error with _socket.send
	{

		byte[] TFTPpayload =  TFTPMessage.createRequestMessage(_requestType, _foreignFilename, _mode);  

		DatagramPacket requestPacket = new DatagramPacket(TFTPpayload, TFTPpayload.length, _inetAddress, TFTP_DEFAULT_PORT);
		_socket.send(requestPacket);

	}



	//-------------------------------------------------------
	// ACKNOWLEDGE
	//-------------------------------------------------------
	private boolean readACK(byte[] packet)
	{

		boolean isOK;

		ByteBuffer packetBuffer = ByteBuffer.wrap(packet);

		int blockNumber = (int) packetBuffer.getShort(2);

		if(blockNumber == _currentBlockNumber)
		{

			isOK = true;

		}
		else
		{

			isOK = false;

		}

		return isOK;

	}


	private void writeACK(int blockNumber) throws IOException 
	{

		byte[] TFTPpayload = TFTPMessage.createACKMessage(blockNumber);

		DatagramPacket ackPacket = new DatagramPacket(TFTPpayload, TFTPpayload.length, _inetAddress, _serverPortNumber);
		_socket.send(ackPacket);

	}



	//-------------------------------------------------------
	// DATA
	//-------------------------------------------------------
	private byte[] readData(byte[] packet) 
	{

		ByteBuffer packetBuffer = ByteBuffer.wrap(packet);

		byte[] data = new byte[packet.length - 4];	//2 bytes for the opcode and 2 bytes for the blocknumber
		
		packetBuffer.position(4);
		packetBuffer.get(data, 0, data.length);
		System.err.println(data.length);
		
		return data;

	}

	private void writeData(int blockNumber, byte[] data) throws IOException 
	{
		byte[] TFTPpayload = TFTPMessage.createDataMessage(blockNumber, data);
		
		DatagramPacket dataPacket = new DatagramPacket(TFTPpayload, TFTPpayload.length, _inetAddress, _serverPortNumber);
		_socket.send(dataPacket);

	}



	//-------------------------------------------------------
	// ERROR
	//-------------------------------------------------------	
	private TFTPErrorStatus readError(byte[] packet) 
	{

		TFTPErrorStatus error;

		ByteBuffer packetBuffer = ByteBuffer.wrap(packet);

		int errorCode = packetBuffer.getShort(2);
		byte[] errorMessage = new byte[packet.length - 5]; 		// -5 ==> 2 byte for opcode, 2 byte for errorcode 
		packetBuffer.get(errorMessage, 0, packet.length - 5);	//TODO the offset may need to be 0. JAVA doc was ambigous as to what to offset was added to, the very start of the array or where the array pointer was currently

		error = new TFTPErrorStatus(errorCode, new String(errorMessage));

		return error;

	}



	private void writeError(int errorCode, String errorMessage) throws IOException
	{

		byte[] TFTPpayload = TFTPMessage.createErrorMessage(errorCode, errorMessage);
		
		DatagramPacket errorPacket = new DatagramPacket(TFTPpayload, TFTPpayload.length, _inetAddress, _serverPortNumber);
		_socket.send(errorPacket);
		
	}



	//-------------------------------------------------------
	// START/STOP
	//-------------------------------------------------------
	public ITFTPStatus start() 	//may return a status object will work it our later
	{

		ITFTPStatus sessionStatus = null;

		try
		{
			/*
			 * Send our request message to the server
			 */
			writeRequest();	

			
			/*
			 * If we are reading from the server
			 */
			if(_requestType == Request.READ)
			{		
				/*
				 * create a new file and fileoutput stream
				 * we will use these to write incoming data blocks 
				 * to a file
				 */
				_file = new File(_localFilename);
				_outputStream = new FileOutputStream(_file);


				/*
				 * Create a buffer and packet object for incoming and outgoing packets
				 * after we create the packet we should Never reference the raw byte[]
				 * always reference the byte[] via the _recievingPacket.getData() method.
				 */
				_receivedPacketBuffer = new byte[TFTP_PACKET_SIZE]; 	//create a buffer to be used as input from server.
				_recievingPacket = new DatagramPacket(_receivedPacketBuffer, 0, _receivedPacketBuffer.length);	//wrapping out buffer in a DatagramPacket. we will no longer reference the actual byte arry directly

				_sendingPacketBuffer = new byte[TFTP_PACKET_SIZE];
				_sendingPacket = new DatagramPacket(_sendingPacketBuffer, 0 , _sendingPacketBuffer.length);

				/*
				 * Read in our First response form the server
				 * it is either a DATA packet with a block code of 1
				 * or an error letting is know that we cannot communicate with the server at this moment.
				 */
				_socket.receive(_recievingPacket);			


				//get the server's port number, we only send request to the default port of 69
				_serverPortNumber = _recievingPacket.getPort();
				System.out.println(_serverPortNumber);	// XXX


				/*
				 * Get the responseCode and block number of the first packet we received
				 */
				int responseCode = readOpcode(_recievingPacket.getData());		
				int blockNumber = readBlockNumber(_recievingPacket.getData());	

				System.out.println("opcode: "+ responseCode + "\n" + "blockNumber: " + blockNumber);	// XXX
				



				/*
				 * if the packet was a DATA packet and had a blockNumber of 1
				 * we are OK to and will begin receiving the rest of the file
				 */
				if(responseCode == TFTPMessage.DATA )//&& blockNumber == 1)
				{

					_recieving = true;
					
					//write data to file
					_dataWriteBuffer = readData(_recievingPacket.getData());
					//System.out.println(new String(_dataWriteBuffer));
					_outputStream.write(_dataWriteBuffer);
					
					
					//send ACK to server
					writeACK(_currentBlockNumber);
					_currentBlockNumber++;		

				}

				/*
				 * if the packet was and error We cannot transfer a file at this moment
				 * set _recieving to false and return errorStatus
				 */
				else
				{

					if(responseCode == TFTPMessage.ERROR)
					{

						System.err.println("ERROR CODE RECIEVED");
						TFTPErrorStatus error = readError(_recievingPacket.getData());

						_recieving = false;

						sessionStatus = error;

						// XXX  return
						return sessionStatus;

					}

				}

				/*
				 * we Received our first data packet from the server
				 * we will now receive the rest and write them to a file 
				 * all while checking the block numbers, as well as src port
				 */
				while(_recieving)
				{

					System.err.println(_recieving);
					
					_socket.receive(_recievingPacket);
					System.err.println("NEW PACKET");

					
					
					responseCode = readOpcode(_recievingPacket.getData());
					blockNumber = readBlockNumber(_recievingPacket.getData());
					
					System.out.println("revieving packet port number: " + _recievingPacket.getPort());	// XXX					
					System.out.println("opcode: " + responseCode);	// XXX
					System.out.println("block number: " + blockNumber);	// XXX
					System.out.println("Packet Length: " + _recievingPacket.getData().length);

					

					//if statements are evaluated from left to right
					if((_recievingPacket.getPort() == _serverPortNumber)	&&	(blockNumber ==_currentBlockNumber)	&&	(responseCode == TFTPMessage.DATA))
					{
						System.err.println("DATA PACKET RECIEVED WITH RIGHT BLOCK NUMBER AND RIGHT PORT");

						_dataWriteBuffer = readData(_recievingPacket.getData());
						_outputStream.write(_dataWriteBuffer);
												
						if(_recievingPacket.getData().length < TFTP_DATA_BLOCK_LENGTH)
						{
							
							System.out.println("LAST PACKET REVIECED");
							_recieving = false;
							sessionStatus = new TFTPOKStatus(TFTPOKStatus.REVCIEVED, "File: " + _foreignFilename + " successfuly recieved from Server: " + _hostname);
							
						}
						else{}
						
						writeACK(_currentBlockNumber);
						
						
						if(_currentBlockNumber < 65535)
						{

							_currentBlockNumber++;

						}
						else
						{

							_currentBlockNumber = 1;

						}
						

					}
					else
					{

						if(responseCode == TFTPMessage.ERROR)
						{

							System.err.println("ERROR CODE RECIEVED");

							sessionStatus = readError(_recievingPacket.getData());

							_recieving = false;
							System.err.println("RECIEVING SET TO FALSE");


						}
						else{
							System.err.println("UNKNOWN OPCODE");
						}//do nothing

					}

				}//end while
				
				_outputStream.close();
				
			}
			
			/*
			 *If we are writing to a server 
			 */
			else
			{
				
				_receivedPacketBuffer = new byte[TFTP_PACKET_SIZE]; 	//create a buffer to be used as input from server.
				_recievingPacket = new DatagramPacket(_receivedPacketBuffer, 0, _receivedPacketBuffer.length);	//wrapping out buffer in a DatagramPacket. we will no longer reference the actual byte arry directly

				_sendingPacketBuffer = new byte[TFTP_PACKET_SIZE];
				_sendingPacket = new DatagramPacket(_sendingPacketBuffer, 0 , _sendingPacketBuffer.length);
				
				_socket.receive(_recievingPacket);			

				/* 
				 * send write request
				 * recieve ACK
				 * LOOP
				 * 	read chunck form file 
				 * 	send chunck
				 * 	wait for ack
				 * end Loop
				 * 
				 * terminate
				 */
			}

		}
		catch(IOException e)
		{

		}


		if(sessionStatus == null)
		{
			sessionStatus = new TFTPErrorStatus(TFTPErrorStatus.NOT_DEFINED, "An Unknown Error has occured");
		}

		return sessionStatus;

	}

}
