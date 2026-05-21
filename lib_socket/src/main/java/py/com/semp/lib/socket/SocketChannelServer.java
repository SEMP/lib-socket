package py.com.semp.lib.socket;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.locks.ReentrantLock;

import py.com.semp.lib.socket.internal.MessageUtil;
import py.com.semp.lib.socket.internal.Messages;
import py.com.semp.lib.utilidades.exceptions.CommunicationException;

public class SocketChannelServer
{
	//TODO en proceso de desarrollo.
	@SuppressWarnings("unused")
	private ServerSocketChannel serverSocketChannel;
	private final ReentrantLock socketLock = new ReentrantLock();
	
	public SocketChannelServer() throws CommunicationException
	{
		this(getNewServerSocketChannel());
	}
	
	public SocketChannelServer(ServerSocketChannel serverSocketChannel)
	{
		super();
		
		this.setServerSocketChannel(serverSocketChannel);
	}
	
	/**
	 * Opens a new socket channel server.
	 *
	 * @return A new {@link ServerSocketChannel} instance.
	 * @throws CommunicationException if an I/O error occurs when opening the socket channel.
	 */
	private static ServerSocketChannel getNewServerSocketChannel() throws CommunicationException
	{
		try
		{
			return ServerSocketChannel.open();
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.CREATE_OBJECT_ERROR, ServerSocketChannel.class.getName());
			
			throw new CommunicationException(errorMessage, e);
		}
	}
	
	/**
	 * Sets the internal {@link ServerSocketChannel} to the specified channel.
	 * This method will lock the socket for thread safety during the operation.
	 *
	 * @param serverSocketChannel the {@link ServerSocketChannel} to set.
	 * @throws NullPointerException if the provided socket channel is null.
	 */
	private void setServerSocketChannel(ServerSocketChannel serverSocketChannel)
	{
		if(serverSocketChannel == null)
		{
			String methodName = "void SocketChannelServer::setServerSocketChannel(ServerSocketChannel)";
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName);
			
			throw new NullPointerException(errorMessage);
		}
		
		this.socketLock.lock();
		
		try
		{
			this.serverSocketChannel = serverSocketChannel;
		}
		finally
		{
			this.socketLock.unlock();
		}
	}
}
