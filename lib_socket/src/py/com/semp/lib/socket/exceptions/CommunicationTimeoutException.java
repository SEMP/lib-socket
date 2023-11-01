package py.com.semp.lib.socket.exceptions;

import py.com.semp.lib.utilidades.exceptions.CommunicationException;

public class CommunicationTimeoutException extends CommunicationException
{
	private static final long serialVersionUID = -3107110459474414366L;
	
	public CommunicationTimeoutException()
	{
		super();
	}
	
	public CommunicationTimeoutException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace)
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}
	
	public CommunicationTimeoutException(String message, Throwable cause)
	{
		super(message, cause);
	}
	
	public CommunicationTimeoutException(String message)
	{
		super(message);
	}
	
	public CommunicationTimeoutException(Throwable cause)
	{
		super(cause);
	}
}