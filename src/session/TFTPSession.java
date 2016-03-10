package session;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import message.TFTPMessage;
import request.Request;
import status.ITFTPStatus;
import status.TFTPErrorStatus;
import status.TFTPOKStatus;



public class TFTPSession 
{

	private static final int 		TFTP_DEFAULT_PORT 		=	69;
	private static final int		TFTP_PACKET_SIZE		=	516; // data is 512 bytes, then 2 bytes for opcode and 2 bytes for block number
	private static final int 		TFTP_DATA_BLOCK_SIZE	=	512;
	private static final String 	CRYPTO_ALG				=	"SHA1PRNG";

	private DatagramSocket		_socket;

	private boolean 			_recieving;
	private boolean				_sending;
	private boolean				_finalPacket;

	//local and server port number attributes.
	private int 				_localPortNumber;
	private int 				_serverPortNumber;

	//general attributes needed throughout the TFTP session
//	private String				_hostname;
	private InetAddress			_inetAddress;
	private Request 			_requestType;
	private String				_foreignFilename;
	private String				_localFilename;
	private String 				_mode;


	//the blockNumber we are expecting
	private int 				_currentBlockNumber;

	//our DatagramPackets that will be used to send and recieve TFTP packets.
	private DatagramPacket 		_recievingPacket;

	//byte arrays backing our DatagramPackets
	private byte[]				_receivedPacketBuffer;

	//byte buffer used to write data blocks to a file
	private byte[]				_dataWriteBuffer;
	private byte[]				_dataReadBuffer;


	private File				_file;
	byte[] 						_fileBuffer;
	private FileOutputStream	_outputStream;
	private FileInputStream		_inputStream;




	//-------------------------------------------------------
	// CONSTRUCTOR
	//-------------------------------------------------------
	public TFTPSession(String hostname, String mode, String foreignFilename, String localFilename, Request request) throws UnknownHostException, SocketException, NoSuchAlgorithmException 
	{

		//_hostname = hostname;
		_foreignFilename = foreignFilename;
		_localFilename = localFilename;
		_mode = mode;

		_requestType = request;

		_recieving = false;

		//Generate Random number between 0 and 65535 to be used as our port number
		//for the transfer session

		try {
			_inetAddress = InetAddress.getByName(hostname);


			SecureRandom rng = SecureRandom.getInstance(CRYPTO_ALG);
			_localPortNumber = rng.nextInt(65535);
			System.out.println(_localPortNumber);

			_socket = new DatagramSocket(_localPortNumber);
		} 
		catch (UnknownHostException e) 
		{
			throw new UnknownHostException("Could not Resolve Host");
		}
		catch (SocketException e) 
		{
			throw new SocketException("Could not bind to socet.");
		}
		catch (NoSuchAlgorithmException e) 
		{
			throw new NoSuchAlgorithmException("Crypto algorithm not supported.");
		}

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
	private byte[] readData(DatagramPacket packet)
	{

		byte[] buffer = new byte[packet.getLength() - 4];
		System.arraycopy(packet.getData(), 4, buffer, 0 ,buffer.length);

		return buffer;

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
	private TFTPErrorStatus readError(DatagramPacket packet)
	{
		TFTPErrorStatus error; 

		int errorCode = (int) packet.getData()[3];
		byte[] errorMessage = new byte[packet.getLength() - 5];
		System.arraycopy(packet.getData(), 4, errorMessage, 0, errorMessage.length);

		error = new TFTPErrorStatus(errorCode, new String(errorMessage, StandardCharsets.US_ASCII).trim());

		return error;
	}

	/*
	private TFTPErrorStatus readError(byte[] packet) 
	{

		TFTPErrorStatus error;

		ByteBuffer packetBuffer = ByteBuffer.wrap(packet);

		int errorCode = packetBuffer.getShort(2);
		byte[] errorMessage = new byte[packet.length - 5]; 		// -5 ==> 2 byte for opcode, 2 byte for errorcode 
		packetBuffer.get(errorMessage, 0, packet.length - 5);	

		error = new TFTPErrorStatus(errorCode, new String(errorMessage));

		return error;

	}
	 */



	private void writeError(int errorCode, String errorMessage, int portNumber) throws IOException
	{

		byte[] TFTPpayload = TFTPMessage.createErrorMessage(errorCode, errorMessage);

		DatagramPacket errorPacket = new DatagramPacket(TFTPpayload, TFTPpayload.length, _inetAddress, portNumber);
		_socket.send(errorPacket);

	}


	private boolean isLastPacket(DatagramPacket packet)
	{

		if(packet.getLength() < 516)
		{
			return true;
		}
		else
		{

			return false;

		}

	}

	private void incrementBlockNumber()
	{
		if(this._currentBlockNumber < 65535)
		{

			_currentBlockNumber++;

		}
		else
		{
			_currentBlockNumber = 1;
		}
	}

	//-------------------------------------------------------
	// START
	//-------------------------------------------------------
	public ITFTPStatus start() 	//may return a status object will work it our later
	{

		ITFTPStatus sessionStatus = null;

		try 
		{

			//send the initial request
			writeRequest();


			/*
			 * 
			 * RECIEVE A FILE
			 * 
			 */
			if(this._requestType == Request.READ)
			{

				_recieving = true;

				_currentBlockNumber = 1;


				//create a new file and output stream to write to that file
				_file = new File(_localFilename);
				_outputStream = new FileOutputStream(_file);

				//create a new byte[] buffer and wrap it in a DatagramPacket
				_receivedPacketBuffer = new byte[TFTP_PACKET_SIZE];
				_recievingPacket = new DatagramPacket(_receivedPacketBuffer, 0, _receivedPacketBuffer.length);

				//listen for a packet
				_socket.receive(_recievingPacket);


				//record the new port number from the server
				_serverPortNumber = _recievingPacket.getPort();


				//Receive the file
				while(_recieving)
				{

					//get the opcode and block number from the new packet
					int opcode = readOpcode(_recievingPacket.getData());
					int blocknumber = readBlockNumber(_recievingPacket.getData());

					if( _recievingPacket.getPort() == _serverPortNumber)
					{
						if(opcode == (int) TFTPMessage.DATA)
						{
							if(blocknumber == _currentBlockNumber)
							{
								//write data to file
								_dataWriteBuffer  = readData(_recievingPacket);	
								_outputStream.write(_dataWriteBuffer);


								//send ack to server
								writeACK(blocknumber);

								incrementBlockNumber();

								//	_currentBlockNumber++;

								//if this is the last data packet we do not want to 
								//listen for more packets and we want to exit the loop
								if(isLastPacket(_recievingPacket))
								{

									_recieving = false;

								}
								// if not the last packet, do nothing
								else{}
							}
							else
							{
								//the packet we received does not have the expected blockNumber
								//TODO resend previous ack?

							}
						}
						else
						{
							//if we did not receive a data packet from the server
							if(opcode == (int) TFTPMessage.ERROR)
							{
								//create a new error status, close the output stream and return 
								sessionStatus = readError(_recievingPacket);

								_outputStream.close();
								_recieving = false;

								return sessionStatus;

							}
							else
							{
								//if the packet was not an error packet or a data packet something went wrong on the server end.
								sessionStatus = new TFTPErrorStatus(-1, "Unknown Error occured, transfer terminated.");

								_outputStream.close();
								_recieving = false;

								return sessionStatus;

							}


						}
					}
					else
					{

						//we Received a packet from another host that is not the server
						//TODO send nonblocking replay to other server and listen for our message again

						int badPortNumber = _socket.getPort();

						writeError(TFTPErrorStatus.UNKNOWN_TID, "This TFTP client did not recognize the packet TID", badPortNumber);

					}	

					//clear the incoming packet buffer so old data is not still in the buffer
					_receivedPacketBuffer = new byte[TFTP_PACKET_SIZE];
					_recievingPacket.setData(_receivedPacketBuffer);

					//if we are still receiving read in a new packet 
					if(_recieving)
					{

						_socket.receive(_recievingPacket);

					}
					else{}	// else do nothing and the while condition will terminate.

				}

				//close the OutputStream and create a new TFTPOKStatus to inform the user of a successful transfer
				_outputStream.close();

				sessionStatus = new TFTPOKStatus(TFTPOKStatus.REVCIEVED, "Sucessfully recieved file: " +_foreignFilename);			

			}//end Recieve





			/*
			 * 
			 * 
			 *SEND A FILE 
			 * 
			 */
			else
			{
				//we are still sending packets
				_sending = true;

				//the incomming ack letting us know that we are ok to transmit will have a block of 0
				_currentBlockNumber = 0;

				//read the data from the file into a buffer from which we will pull data from to send
				_file = new File(_localFilename);
				_inputStream = new FileInputStream(_file);

				_dataReadBuffer = new byte[(int) _file.length()];

				_inputStream.read(_dataReadBuffer);
				_inputStream.close();

				ByteBuffer fileBuffer = ByteBuffer.wrap(_dataReadBuffer);


				//create a new byte[] buffer and wrap it in a DatagramPacket
				_receivedPacketBuffer = new byte[TFTP_PACKET_SIZE];
				_recievingPacket = new DatagramPacket(_receivedPacketBuffer, 0, _receivedPacketBuffer.length);

				//listen for a packet
				_socket.receive(_recievingPacket);

				//record the new port number from the server
				_serverPortNumber = _recievingPacket.getPort();


				while(_sending)
				{

					//get the opcode and block number from the new packet
					int opcode = readOpcode(_recievingPacket.getData());
					//int blocknumber = readBlockNumber(_recievingPacket.getData());

					if( _recievingPacket.getPort() == _serverPortNumber)
					{

						if(opcode == (int) TFTPMessage.ACK)
						{

							if(readACK(_recievingPacket.getData()))
							{

								incrementBlockNumber();

								

								if(!_finalPacket)
								{
									byte[] data;

									int remainingBytes = (int) (_file.length() - fileBuffer.position());
									
									if(remainingBytes >= TFTP_DATA_BLOCK_SIZE)
									{
										data = new byte[TFTP_DATA_BLOCK_SIZE];
										fileBuffer.get(data, 0, TFTP_DATA_BLOCK_SIZE);
										_finalPacket = false;
									}
									else
									{
										data = new byte[remainingBytes];
										fileBuffer.get(data, 0, remainingBytes);
										_finalPacket = true;
									}
									
									writeData(_currentBlockNumber, data);
									System.out.println("writing dataPacket with blocknumber: " + _currentBlockNumber + " of length: " + data.length );
								}
								else
								{
									_sending = false;
								}

							}
							else
							{
								//TODO IDK what to do 
							}
						}
						else	//opcode was not an ack
						{
							if(opcode == (int) TFTPMessage.ERROR)
							{

								sessionStatus = readError(_recievingPacket);
								_sending = false;

								return sessionStatus;

							}
							else
							{
								
								sessionStatus = new TFTPErrorStatus(-1, "Unknown Error occured, transfer terminated.");
								_sending = false;

								return sessionStatus;
								
							}
						}
					}
					else
					{

						//we Received a packet from another host that is not the server
						//TODO send nonblocking replay to other server and listen for our message again

						int badPortNumber = _socket.getPort();

						writeError(TFTPErrorStatus.UNKNOWN_TID, "This TFTP client did not recognize the packet TID", badPortNumber);

					}

					
					
					//clear the incoming packet buffer so old data is not still in the buffer
					_receivedPacketBuffer = new byte[TFTP_PACKET_SIZE];
					_recievingPacket.setData(_receivedPacketBuffer);

					
					
					//if we are still sending Data in  
					if(_sending)
					{

						_socket.receive(_recievingPacket);

					}
					else{}	// else do nothing and the while condition will terminate.



				}

				// create a new TFTPOKStatus to inform the user of a successful transfer
				sessionStatus = new TFTPOKStatus(TFTPOKStatus.REVCIEVED, "Sucessfully sent file: " +_foreignFilename);	

			}	//end Send

		} 
		catch (IOException e) 
		{

			e.printStackTrace();
		}
		finally
		{
			if(sessionStatus == null)
			{
				sessionStatus = new TFTPErrorStatus(-1, "Unknown Error occured, transfer terminated.");
			}
		}


		return sessionStatus;

	}

}
