package status;

public class TFTPErrorStatus implements ITFTPStatus
{
	public static final int 	NOT_DEFINED			=	0;
	public static final int 	FILE_NOT_FOUND		=	1;
	public static final int		ACCESS_VIOLATION	=	2;
	public static final int		DISK_FULL			=	3;
	public static final int		ILLEGAL_OPERATION	=	4;
	public static final int		UNKNOWN_TID			=	5;
	public static final int		FILE_ALREADY_EXISTS	=	6;
	public static final int		NO_SUCH_USER		=	7;
	
	
	private int 		_errorCode;
	private String		_errorMessage;
	
	
	public TFTPErrorStatus(int errorCode, String errorMessage)
	{
		_errorCode = errorCode;
		_errorMessage = errorMessage;
	}
	


	@Override
	public String getStatusMessage() 
	{
		
		return _errorMessage;
		
	}



	@Override
	public int getStatusCode() 
	{
		
		return _errorCode;
		
	}
	
	
	@Override
	public String toString()
	{
		
		String errorString = "ERROR: \n"
						   + "Error Code: " + _errorCode + "\n "
						   + "Error Message: " + _errorMessage;
					
		return errorString;
		
	}
	
	

}
