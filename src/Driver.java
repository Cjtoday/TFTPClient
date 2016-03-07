import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

import client.TFTPClient;
import request.Request;

public class Driver 
{
	
	String hostname;
	Request request;
	String filename;
	
	
	public static void main(String args[])
	{
		try 
		{
			
			System.out.println(	TFTPClient.recieve("localhost", "netascii", "ReadMeMT.txt", "C:\\Users\\CPenz11\\Pictures\\Assets\\test").toString());
			
		} 
		catch (SocketException | UnknownHostException | NoSuchAlgorithmException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
