package exception;

public class UnknownOPCodeException extends Exception
{
	public UnknownOPCodeException(String context)
	{
		super(context);
	}
}
