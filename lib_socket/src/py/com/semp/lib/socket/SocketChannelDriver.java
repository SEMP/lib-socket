package py.com.semp.lib.socket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;

import py.com.semp.lib.socket.configuration.SocketConfiguration;
import py.com.semp.lib.socket.configuration.Values;
import py.com.semp.lib.socket.exceptions.ConnectionClosedException;
import py.com.semp.lib.socket.exceptions.CommunicationTimeoutException;
import py.com.semp.lib.socket.internal.MessageUtil;
import py.com.semp.lib.socket.internal.Messages;
import py.com.semp.lib.utilidades.communication.DataInterface;
import py.com.semp.lib.utilidades.communication.DataReceiver;
import py.com.semp.lib.utilidades.communication.DataTransmitter;
import py.com.semp.lib.utilidades.communication.ShutdownHookAction;
import py.com.semp.lib.utilidades.communication.listeners.ConnectionEventListener;
import py.com.semp.lib.utilidades.communication.listeners.DataListener;
import py.com.semp.lib.utilidades.configuration.ConfigurationValues;
import py.com.semp.lib.utilidades.exceptions.CommunicationException;
import py.com.semp.lib.utilidades.log.Logger;
import py.com.semp.lib.utilidades.log.LoggerManager;
import py.com.semp.lib.utilidades.utilities.ArrayUtils;

public class SocketChannelDriver implements DataInterface, DataReceiver, DataTransmitter
{
	private static final Logger LOGGER = LoggerManager.getLogger(Values.Constants.SOCKET_CONTEXT);
	
	private SocketChannel socketChannel;
	private volatile String stringIdentifier;
	private SocketConfiguration configurationValues;
	private final Thread shutdownHook = new Thread(new ShutdownHookAction(this));
	private final CopyOnWriteArraySet<DataListener> dataListeners = new CopyOnWriteArraySet<>();
	private final CopyOnWriteArraySet<ConnectionEventListener> connectionEventListeners = new CopyOnWriteArraySet<>();
	private final ExecutorService executorService = Executors.newFixedThreadPool(Values.Constants.SOCKET_LISTENERS_THREAD_POOL_SIZE);
	private final ReentrantLock socketLock = new ReentrantLock();
	private volatile boolean shuttingDown = false;
	private ByteBuffer readBuffer;
	
	public SocketChannelDriver() throws CommunicationException
	{
		this(getNewSocketChannel());
	}

	public SocketChannelDriver(SocketChannel socketChannel) throws CommunicationException
	{
		super();
		
		this.setSocketChannel(socketChannel);
		
		this.addShutdownHook();
	}
	
	private static SocketChannel getNewSocketChannel() throws CommunicationException
	{
		try
		{
			return SocketChannel.open();
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.CREATE_OBJECT_ERROR, SocketChannel.class.getName());
			
			throw new CommunicationException(errorMessage, e);
		}
	}
	
	private void addShutdownHook()
	{
		Runtime.getRuntime().addShutdownHook(this.shutdownHook);
	}
	
	private void removeShutdownHook()
	{
		Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
	}
	
	private void setSocketChannel(SocketChannel socketChannel) throws CommunicationException
	{
		if(socketChannel == null)
		{
			String methodName = "void SocketChannelDriver::setSocketChannel(SocketChannel)";
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName);
			
			throw new NullPointerException(errorMessage);
		}
		
		this.socketLock.lock();
		
		try
		{
			this.socketChannel = socketChannel;
		}
		finally
		{
			this.socketLock.unlock();
		}
	}
	
	public SocketChannel getSocketChannel()
	{
		return this.socketChannel;
	}
	
	@Override
	public void sendData(byte[] data) throws CommunicationException
	{
		int writeTimeout = this.configurationValues.getValue(Values.VariableNames.WRITE_TIMEOUT_MS);
		long start = System.nanoTime();
		long timeoutNano = writeTimeout * 1000000L;
		
		if(!this.socketChannel.isConnected())
		{
			String errorMessage = MessageUtil.getMessage(Messages.SOCKET_CLOSED_OR_NOT_CONNECTED_ERROR, this.getStringIdentifier());
			
			throw new ConnectionClosedException(errorMessage);
		}
		
		if(data == null || data.length == 0)
		{
			return;
		}
		
		this.socketLock.lock();
		
		try
		{
			if(!this.socketChannel.isConnected())
			{
				String errorMessage = MessageUtil.getMessage(Messages.SOCKET_CLOSED_OR_NOT_CONNECTED_ERROR, this.getStringIdentifier());
				
				throw new ConnectionClosedException(errorMessage);
			}
			
			ByteBuffer buffer = ByteBuffer.wrap(data);
			
			LOGGER.debug(String.format("Sending %d bytes (%s): %s", data.length, this.getStringIdentifier(), ArrayUtils.toHexaArrayString(data))); //"Sending (" + this.getStringIdentifier() + "): " + ArrayUtils.toHexaArrayString(data));
			
			while(buffer.hasRemaining())
			{
				if(this.shuttingDown)
				{
					String methodName = "byte[] SocketChannelDriver::readData()";
					
					String errorMessage = MessageUtil.getMessage(Messages.TASK_SHUTDOWN_ERROR, methodName);
					
					throw new CommunicationException(errorMessage);
				}
				
				if(timeoutNano >= 0 && System.nanoTime() - start >= timeoutNano)
				{
					String errorMessage = MessageUtil.getMessage(Messages.WRITTING_TIMOUT_ERROR, data.length, this.configurationValues.toString());
					
					throw new CommunicationTimeoutException(errorMessage);
				}
				
				int bytesWritten = socketChannel.write(buffer);
				
				if(bytesWritten < 0)
				{
					String errorMessage = MessageUtil.getMessage(Messages.FAILED_TO_SEND_DATA_ERROR, this.getStringIdentifier());
					
					throw new CommunicationException(errorMessage);
				}
			}
			
			this.informSent(data);
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.FAILED_TO_SEND_DATA_ERROR, this.getStringIdentifier());
			
			throw new CommunicationException(errorMessage, e);
		}
		finally
		{
			this.socketLock.unlock();
		}
	}
	
	private void informSent(byte[] data)
	{
		try
	    {
	        this.executorService.submit(() ->
	        {
	        	for(DataListener listener : this.dataListeners)
	            {
	                if(this.shuttingDown)
	                {
	                    return;
	                }
	                
	                try
	                {
	                	listener.onDataSent(Instant.now(), this, data);
	                }
	                catch(RuntimeException e)
	                {
	                    String errorMessage = MessageUtil.getMessage(Messages.LISTENER_THROWN_EXCEPTION_ERROR, listener.getClass().getName());
	                    
	                    LOGGER.warning(errorMessage, e);
	                }
	            }
	        });
	    }
	    catch(RejectedExecutionException e)
	    {
			String methodName = "void SocketChannelDriver::informSent(byte[])";
			
	        String errorMessage = MessageUtil.getMessage(Messages.TASK_SHUTDOWN_ERROR, methodName);
	        
	        LOGGER.debug(errorMessage, e);
	    }
	}
	
	@Override
	public SocketChannelDriver addDataListeners(DataListener... listeners)
	{
		if(listeners == null)
		{
			String methodName = "SocketChannelDriver SocketChannelDriver::addDataListeners(DataListener...)";
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName);
			
			throw new NullPointerException(errorMessage);
		}
		
		this.dataListeners.addAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SocketChannelDriver removeDataListeners(DataListener... listeners)
	{
		if(listeners == null)
		{
			String methodName = "SocketChannelDriver SocketChannelDriver::removeDataListeners(DataListener...)";
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName);
			
			throw new NullPointerException(errorMessage);
		}
		
		this.dataListeners.removeAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SocketChannelDriver removeAllDataListeners()
	{
		this.dataListeners.clear();
		
		return this;
	}
	
	@Override
	public byte[] readData() throws CommunicationException
	{
		ByteBuffer buffer = this.getReadBuffer();
		
		int readTimeout = this.configurationValues.getValue(Values.VariableNames.READ_TIMEOUT_MS);
		long start = System.nanoTime();
		long timeoutNano = readTimeout * 1000000L;
		
		int bytesRead = 0;
		
		while(bytesRead == 0)
		{
			if(this.shuttingDown)
			{
				String methodName = "byte[] SocketChannelDriver::readData()";
				
				String errorMessage = MessageUtil.getMessage(Messages.TASK_SHUTDOWN_ERROR, methodName);
				
				throw new CommunicationException(errorMessage);
			}
			
			if(timeoutNano >= 0 && System.nanoTime() - start >= timeoutNano)
			{
				String errorMessage = MessageUtil.getMessage(Messages.READING_TIMOUT_ERROR, this.configurationValues.toString());
				
				throw new CommunicationTimeoutException(errorMessage);
			}
			
			try
			{
				bytesRead = this.socketChannel.read(buffer);
			}
			catch(IOException e)
			{
				String errorMessage = MessageUtil.getMessage(Messages.FAILED_TO_RECEIVE_DATA_ERROR, this.getStringIdentifier());
				
				throw new CommunicationException(errorMessage, e);
			}
			
			try
			{
				Thread.sleep(Values.Constants.POLL_DELAY_MS);
			}
			catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
				
				String errorMessage = MessageUtil.getMessage(Messages.FAILED_TO_RECEIVE_DATA_ERROR, this.getStringIdentifier());
				
				throw new CommunicationException(errorMessage, e);
			}
		}
		
		if(bytesRead == -1)
		{
			String errorMessage = MessageUtil.getMessage(Messages.END_OF_STREAM_REACHED, this.getStringIdentifier());
			
			throw new ConnectionClosedException(errorMessage);
		}
		
		buffer.flip();
		
		byte[] data = new byte[bytesRead];
		
		buffer.get(data);
		
		this.informReceived(data);
		
		return data;
	}
	
	private ByteBuffer getReadBuffer()
	{
		int bufferSize = this.configurationValues.getValue(Values.VariableNames.SOCKET_BUFFER_SIZE_BYTES);
		
		if(this.readBuffer == null || this.readBuffer.capacity() < bufferSize)
		{
			this.readBuffer = ByteBuffer.allocate(bufferSize);
		}
		
		this.readBuffer.clear();
		
		return this.readBuffer;
	}
	
	private void informReceived(byte[] data)
	{
		try
	    {
	        this.executorService.submit(() ->
	        {
	        	for(DataListener listener : this.dataListeners)
	            {
	                if(this.shuttingDown)
	                {
	                    return;
	                }
	                
	                try
	                {
	                	listener.onDataReceived(Instant.now(), this, data);
	                }
	                catch(RuntimeException e)
	                {
	                    String errorMessage = MessageUtil.getMessage(Messages.LISTENER_THROWN_EXCEPTION_ERROR, listener.getClass().getName());
	                    
	                    LOGGER.warning(errorMessage, e);
	                }
	            }
	        });
	    }
	    catch(RejectedExecutionException e)
	    {
			String methodName = "void SocketChannelDriver::informSent(byte[])";
			
	        String errorMessage = MessageUtil.getMessage(Messages.TASK_SHUTDOWN_ERROR, methodName);
	        
	        LOGGER.debug(errorMessage, e);
	    }
	}

	@Override
	public SocketChannelDriver connect() throws CommunicationException
	{
		this.socketLock.lock();
		try
		{
			if(this.shuttingDown)
			{
				String methodName = "SocketChannelDriver SocketChannelDriver::connect()";
				
				String errorMessage = MessageUtil.getMessage(Messages.TASK_SHUTDOWN_ERROR, methodName);
				
				throw new CommunicationException(errorMessage);
			}
			
			if(this.socketChannel != null && this.socketChannel.isConnected())
			{
				String errorMessage = MessageUtil.getMessage(Messages.ALREADY_CONNECTED_ERROR, this.getStringIdentifier());
				
				throw new ConnectionClosedException(errorMessage);
			}
			
			if(this.socketChannel == null)
			{
				this.socketChannel = SocketChannel.open();
			}
			
			String address = this.configurationValues.getValue(Values.VariableNames.REMOTE_ADDRESS);
			int port = this.configurationValues.getValue(Values.VariableNames.REMOTE_PORT);
			int connectionTimeout = this.configurationValues.getValue(Values.VariableNames.CONNECTION_TIMEOUT_MS);
			
			this.socketChannel.configureBlocking(false);
			
			this.waitConnection(new InetSocketAddress(address, port), connectionTimeout * 1000000L);
			
			this.getStringIdentifier();
			
			this.informConnected();
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.CONNECTING_ERROR, this.configurationValues.toString());
			
			throw new CommunicationException(errorMessage, e);
		}
		finally
		{
			this.socketLock.unlock();
		}
		
		return this;
	}
	
	private void waitConnection(InetSocketAddress address, long timeoutNano) throws CommunicationException
	{
		long start = System.nanoTime();
		
		try
		{
			boolean connected = this.socketChannel.connect(address);
			
			if(!connected)
			{
				while(!this.socketChannel.finishConnect())
				{
					if(this.shuttingDown)
					{
						String methodName = "void SocketChannelDriver::waitConnection(InetSocketAddress)";
						
						String errorMessage = MessageUtil.getMessage(Messages.TASK_SHUTDOWN_ERROR, methodName);
						
						throw new CommunicationException(errorMessage);
					}
					
					if(timeoutNano >= 0 && System.nanoTime() - start >= timeoutNano)
					{
						String errorMessage = MessageUtil.getMessage(Messages.CONNECTING_TIMOUT_ERROR, this.configurationValues.toString());
						
						throw new CommunicationTimeoutException(errorMessage);
					}
					
					try
					{
						Thread.sleep(Values.Constants.POLL_DELAY_MS);
					}
					catch(InterruptedException e)
					{
						Thread.currentThread().interrupt();
						
						String errorMessage = MessageUtil.getMessage(Messages.CONNECTING_ERROR, this.configurationValues.toString());
						
						throw new CommunicationException(errorMessage, e);
					}
				}
			}
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.CONNECTING_ERROR, this.configurationValues.toString());
			
			throw new CommunicationException(errorMessage, e);
		}
	}
	
	private void informConnected()
	{
		try
	    {
	        this.executorService.submit(() ->
	        {
	            for(ConnectionEventListener listener : this.connectionEventListeners)
	            {
	                if(this.shuttingDown)
	                {
	                    return;
	                }
	                
	                try
	                {
	                    listener.onConnect(Instant.now(), this);
	                }
	                catch(RuntimeException e)
	                {
	                    String errorMessage = MessageUtil.getMessage(Messages.LISTENER_THROWN_EXCEPTION_ERROR, listener.getClass().getName());
	                    
	                    LOGGER.warning(errorMessage, e);
	                }
	            }
	        });
	    }
	    catch(RejectedExecutionException e)
	    {
			String methodName = "void SocketChannelDriver::informConnected()";
			
	        String errorMessage = MessageUtil.getMessage(Messages.TASK_SHUTDOWN_ERROR, methodName);
	        
	        LOGGER.debug(errorMessage, e);
	    }
	}

	@Override
	public SocketChannelDriver connect(ConfigurationValues configurationValues) throws CommunicationException
	{
		this.setConfigurationValues(configurationValues);
		
		return this.connect();
	}
	
	public SocketChannelDriver connect(String address, int port) throws CommunicationException
	{
		SocketConfiguration configurationValues = new SocketConfiguration();
		
		configurationValues.setParameter(Values.VariableNames.REMOTE_ADDRESS, address);
		configurationValues.setParameter(Values.VariableNames.REMOTE_PORT, port);
		
		return this.connect(configurationValues);
	}
	
	@Override
	public SocketChannelDriver disconnect() throws CommunicationException
	{
		this.socketLock.lock();
		
		try
		{
			this.informDisconnected();
			this.executorService.shutdown();
			
			if(this.socketChannel != null && this.socketChannel.isOpen())
			{
				try
				{
					this.socketChannel.close();
				}
				finally
				{
					this.removeShutdownHook();
				}
			}
			
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.DISCONNECTING_ERROR, this.getStringIdentifier());
			
			throw new CommunicationException(errorMessage, e);
		}
		finally
		{
			this.socketLock.unlock();
		}
		
		return this;
	}
	
	public SocketChannelDriver shutdown() throws CommunicationException
	{
		this.shuttingDown = true;
		
		this.socketLock.lock();
		
		try
		{
			this.executorService.shutdownNow();
			
			if(this.socketChannel != null && this.socketChannel.isOpen())
			{
				try
				{
					this.socketChannel.close();
				}
				finally
				{
					this.removeShutdownHook();
				}
			}
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.SHUTDOWN_ERROR, this.getStringIdentifier());
			
			throw new CommunicationException(errorMessage, e);
		}
		finally
		{
			this.socketLock.unlock();
		}
		
		return this;
	}
	
	private void informDisconnected()
	{
	    try
	    {
	        this.executorService.submit(() ->
	        {
	            for(ConnectionEventListener listener : this.connectionEventListeners)
	            {
	                if(this.shuttingDown)
	                {
	                    return;
	                }
	                
	                try
	                {
	                    listener.onDisconnect(Instant.now(), this);
	                }
	                catch(RuntimeException e)
	                {
	                    String errorMessage = MessageUtil.getMessage(Messages.LISTENER_THROWN_EXCEPTION_ERROR, listener.getClass().getName());
	                    
	                    LOGGER.warning(errorMessage, e);
	                }
	            }
	        });
	    }
	    catch(RejectedExecutionException e)
	    {
			String methodName = "void SocketChannelDriver::informDisconnected()";
			
	        String errorMessage = MessageUtil.getMessage(Messages.TASK_SHUTDOWN_ERROR, methodName);
	        
	        LOGGER.debug(errorMessage, e);
	    }
	}
	
	@Override
	public SocketChannelDriver setConfigurationValues(ConfigurationValues configurationValues) throws CommunicationException
	{
		this.checkConfigurationValues(configurationValues);
		
		this.configurationValues = (SocketConfiguration) configurationValues;
		
		return this;
	}
	
	/**
	 * Validates the given ConfigurationValues object to ensure it's of the right type
	 * and has all required parameters set.
	 *
	 * @param configurationValues The ConfigurationValues object to validate.
	 * @throws CommunicationException if the object is of the wrong type or missing required parameters.
	 */
	private void checkConfigurationValues(ConfigurationValues configurationValues) throws CommunicationException
	{
		if(!(configurationValues instanceof SocketConfiguration))
		{
			String requiredClassName = SocketConfiguration.class.getName();
			String currentClassName = this.getClass().getName();
			
			String errorMessage = MessageUtil.getMessage(Messages.WRONG_CONFIGURATION_OBJECT_ERROR, requiredClassName, currentClassName);
			
			throw new CommunicationException(errorMessage);
		}
		
		if(!configurationValues.checkRequiredParameters())
		{
			String errorMessage = MessageUtil.getMessage(Messages.REQUIRED_CONFIGURATION_VALUE_NOT_FOUND_ERROR);
			
			throw new CommunicationException(errorMessage);
		}
	}
	
	@Override
	public SocketConfiguration getConfigurationValues()
	{
		return this.configurationValues;
	}
	
	@Override
	public SocketChannelDriver addConnectionEventListeners(ConnectionEventListener... listeners)
	{
		if(listeners == null)
		{
			String methodName = "SocketChannelDriver SocketChannelDriver::addConnectionEventListeners(ConnectionEventListener...)";
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName);
			
			throw new NullPointerException(errorMessage);
		}
		
		this.connectionEventListeners.addAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SocketChannelDriver removeConnectionEventListeners(ConnectionEventListener... listeners)
	{
		if(listeners == null)
		{
			String methodName = "SocketChannelDriver SocketChannelDriver::removeConnectionEventListeners(ConnectionEventListener...)";
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName);
			
			throw new NullPointerException(errorMessage);
		}
		
		this.connectionEventListeners.removeAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SocketChannelDriver removeAllConnectionEventListeners()
	{
		this.connectionEventListeners.clear();
		
		return this;
	}
	
	@Override
	public String getStringIdentifier()
	{
		if(this.stringIdentifier == null)
		{
			SocketAddress remoteAddress = this.getRemoteAddress();
			SocketAddress localAddress = this.getLocalAddress();
			
			StringBuilder sb = new StringBuilder();
			
			sb.append("L[").append(getAddressString(localAddress)).append("]");
			sb.append("<->");
			sb.append("R[").append(getAddressString(remoteAddress)).append("]");
			
			this.stringIdentifier = sb.toString();
		}
		
		return this.stringIdentifier;
	}
	
	public SocketAddress getRemoteAddress()
	{
		try
		{
			return this.socketChannel.getRemoteAddress();
		}
		catch(IOException e)
		{
			String methodName = "SocketAddress SocketChannelDriver::getRemoteAddress()";
			
			String errorMessage = MessageUtil.getMessage(Messages.UNABLE_TO_OBTAIN_VALUE_ERROR, "remoteAddress", methodName);
			
			LOGGER.error(errorMessage, e);
			
			return null;
		}
	}
	
	public SocketAddress getLocalAddress()
	{
		try
		{
			return this.socketChannel.getLocalAddress();
		}
		catch(IOException e)
		{
			String methodName = "SocketAddress SocketChannelDriver::getLocalAddress()";
			
			String errorMessage = MessageUtil.getMessage(Messages.UNABLE_TO_OBTAIN_VALUE_ERROR, "localAddress", methodName);
			
			LOGGER.error(errorMessage, e);
			
			return null;
		}
	}
	
	public static String getAddressString(SocketAddress socketAddress)
	{
		if(socketAddress == null)
		{
			return Values.Utilities.Constants.NULL_VALUE_STRING;
		}
		
		if(socketAddress instanceof InetSocketAddress)
		{
			InetSocketAddress inetSocketAddress = (InetSocketAddress)socketAddress;
			
			InetAddress address = inetSocketAddress.getAddress();
			int port = inetSocketAddress.getPort();
			
			StringBuilder sb = new StringBuilder();
			
			sb.append(address.getHostAddress());
			sb.append(":");
			sb.append(port);
			
			return sb.toString();
		}
		
		return socketAddress.toString();
	}
	
	@Override
	public boolean isConnected()
	{
		if(this.socketChannel == null)
		{
			return false;
		}
		
		return this.socketChannel.isConnected();
	}
}