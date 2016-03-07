/*
 * Author: Cameron Penz 
 * 	email: CPenz11@winona.edu ,  CPenz94@outlook.com
 * 	Date: 3/4/2016
 * 	
 * 	TFTP Client Based off of RFC 1350 
 * 
 * 
 * 
 */



package client;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;

import request.Request;
import session.TFTPSession;
import status.ITFTPStatus;

public class TFTPClient 
{
	
	
	
		
	public static ITFTPStatus send(String hostname, String mode, String foreignFilename, String localFilename) throws SocketException, UnknownHostException, NoSuchAlgorithmException 
	{
		
		TFTPSession transferSession = new TFTPSession(hostname, mode,foreignFilename, localFilename, Request.WRITE);
		
		ITFTPStatus status;
		status = transferSession.start();
	
		return status;
		
	}
	
	
	
	public static ITFTPStatus recieve(String hostname, String mode, String foreignFilename, String localFilename) throws SocketException, UnknownHostException, NoSuchAlgorithmException
	{
		
		TFTPSession transferSession = new TFTPSession(hostname, mode, foreignFilename, localFilename, Request.READ);
		
		ITFTPStatus status;
		status = transferSession.start();
	
		return status;
		
	}
}
