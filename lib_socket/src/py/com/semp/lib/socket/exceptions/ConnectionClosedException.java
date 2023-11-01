package py.com.semp.lib.socket.exceptions;

import py.com.semp.lib.utilidades.exceptions.CommunicationException;

public class ConnectionClosedException extends CommunicationException
{
	private static final long serialVersionUID = -3559924976436305597L;
	
	public ConnectionClosedException()
	{
		super();
	}
	
	public ConnectionClosedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace)
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}
	
	public ConnectionClosedException(String message, Throwable cause)
	{
		super(message, cause);
	}
	
	public ConnectionClosedException(String message)
	{
		super(message);
	}
	
	public ConnectionClosedException(Throwable cause)
	{
		super(cause);
	}
}