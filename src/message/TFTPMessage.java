package message;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import request.Request;

/*
 * all message constructor will be private. we will only create message objects
 * when we read in or write our a message 
 */


public class TFTPMessage 
{
	
	public final static int 	TFTP_DATA_BLOCK_LENGTH	= 	512;
	public final static int		TFTP_PACKET_SIZE		=	516;
	public final static byte 	OPCODE_PREFIX 	=	0;
	
	//opcodes
	public static final byte 	RRQ 	= 	(byte) 1;
	public static final byte 	WRQ 	=	(byte) 2;
	public static final byte 	DATA 	=	(byte) 3;
	public static final byte 	ACK 	=	(byte) 4;
	public static final byte 	ERROR	=	(byte) 5;

	
	
	//-------------------------------------------------------
	// REQUEST
	//-------------------------------------------------------
	public static byte[] createRequestMessage(Request req, String foreignFilename, String mode)
	{
		
		byte[] filenameByteArray = foreignFilename.getBytes(StandardCharsets.US_ASCII);
		byte[] modeByteArray = mode.getBytes(StandardCharsets.US_ASCII);
		
		byte[] opcode = new byte[2];
		
		opcode[0] = OPCODE_PREFIX;
		if(req == Request.READ)
		{
			opcode[1] = RRQ;
		}
		else
		{
			opcode[1] = WRQ;
		}
		
		byte[] tftpPacket = new byte[2+ filenameByteArray.length + 1 + modeByteArray.length +1 ];
		
		ByteBuffer packetBuffer = ByteBuffer.wrap(tftpPacket);
		
		packetBuffer.put(opcode);
		packetBuffer.put(filenameByteArray);
		packetBuffer.put((byte)0);
		packetBuffer.put(modeByteArray);
		packetBuffer.put((byte) 0);
		
		packetBuffer.flip();
		
		return packetBuffer.array();
		
	}
	
	
	//-------------------------------------------------------
	// DATA
	//-------------------------------------------------------
	public static byte[] createDataMessage(int blockNumber, byte[] data)
	{	
		
		byte[] blockNumberByteArray = new byte[2];
		blockNumberByteArray[0] = (byte) (blockNumber & 0xFF);
		blockNumberByteArray[1] = (byte) ((blockNumber >>> 8) & 0xFF);
		
		byte[] opcode = new byte[2];
		opcode[0] = OPCODE_PREFIX;
		opcode[1] = DATA;
	
		byte[] tftpPacket = new byte[data.length + 4];
		
		ByteBuffer packetBuffer = ByteBuffer.wrap(tftpPacket);
		
		packetBuffer.put(opcode);
		packetBuffer.put(blockNumberByteArray[1]);
		packetBuffer.put(blockNumberByteArray[0]);
		
		
		packetBuffer.put(data);

		
		packetBuffer.flip();
		
		return packetBuffer.array();
		
	}
	
	
	//-------------------------------------------------------
	// ACK
	//-------------------------------------------------------
	public static byte[] createACKMessage(int blockNumber)
	{
		
		byte[] blockNumberByteArray = new byte[2];
		blockNumberByteArray[0] = (byte) (blockNumber & 0xFF);
		blockNumberByteArray[1] = (byte) ((blockNumber >>> 8) & 0xFF);
		
		byte[] opcode = new byte[2];
		opcode[0] = (byte) OPCODE_PREFIX;
		opcode[1] = (byte) ACK;
		
		byte[] tftpPacket = new byte[4];
		ByteBuffer packetBuffer = ByteBuffer.wrap(tftpPacket);
		
		
		packetBuffer.put(opcode);
		packetBuffer.put(blockNumberByteArray[1]);
		packetBuffer.put(blockNumberByteArray[0]);
			
		System.out.println("sending ACK for packet: " + blockNumber + " as: " + blockNumberByteArray[1]+""+blockNumberByteArray[0]);
		
		packetBuffer.flip();
		
		return packetBuffer.array();
 
	}
	
	
	
	//-------------------------------------------------------
	// ERROR
	//-------------------------------------------------------
	public static byte[] createErrorMessage(int errorCode, String errorMessage)
	{
		
		byte[] errorCodeByteArray = new byte[2];
		errorCodeByteArray[0] = (byte) 0;
		errorCodeByteArray[1] = (byte) errorCode;
		
		byte[] errorMessageAsByteArray = errorMessage.getBytes(StandardCharsets.US_ASCII);
		
		byte[] opcode = new byte[2];
		opcode[0] = OPCODE_PREFIX;
		opcode[1] = ERROR;
		
		byte[] tftpPacket = new byte[2 + 2 +errorMessageAsByteArray.length + 1];
		ByteBuffer packetBuffer = ByteBuffer.wrap(tftpPacket);
		
		packetBuffer.put(opcode);
		packetBuffer.put(errorCodeByteArray);
		packetBuffer.put(errorMessageAsByteArray);
		packetBuffer.put((byte) 0);
		
		packetBuffer.flip();
		
		return packetBuffer.array();
		
	}
	
}




