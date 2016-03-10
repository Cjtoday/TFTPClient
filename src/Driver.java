import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;


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
			
			System.out.println(	TFTPClient.send("localhost", "octet", "pazaak_cards.jpg", "C:\\Users\\CPenz11\\Pictures\\Assets\\pazaak_cards.jpg").toString());
			
		}
		catch (UnknownHostException | SocketException | NoSuchAlgorithmException e) 
		{
			System.out.println(e.getMessage());
			//e.printStackTrace();
		}



	}
}
