package status;

public class TFTPOKStatus implements ITFTPStatus 
{

	public static final int REVCIEVED 	=	0;
	public static final int SENT		=	1;
	
	
	int 	_statusCode;
	String 	_statusMessage;
	
	
	public TFTPOKStatus(int statusCode, String statusMessage)
	{
		
		_statusCode = statusCode;
		_statusMessage = statusMessage;
		
	}
	
	
	@Override
	public String getStatusMessage() 
	{

		return null;
		
	}

	
	
	@Override
	public int getStatusCode() 
	{
		
		return 0;
		
	}
	
	
	@Override
	public String toString()
	{
		
		
	
		return _statusMessage;
		
	}

}
