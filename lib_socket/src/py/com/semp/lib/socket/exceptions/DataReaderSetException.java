package py.com.semp.lib.socket.exceptions;

import py.com.semp.lib.utilidades.exceptions.CommunicationException;

/**
 * Exception thrown when attempting to set a DataReader on a SocketChannelDriver
 * that already has a DataReader set.
 */
public class DataReaderSetException extends CommunicationException
{
	private static final long serialVersionUID = -2967094013957672978L;

	public DataReaderSetException()
	{
		super();
	}
	
	public DataReaderSetException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace)
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}
	
	public DataReaderSetException(String message, Throwable cause)
	{
		super(message, cause);
	}
	
	public DataReaderSetException(String message)
	{
		super(message);
	}
	
	public DataReaderSetException(Throwable cause)
	{
		super(cause);
	}
}