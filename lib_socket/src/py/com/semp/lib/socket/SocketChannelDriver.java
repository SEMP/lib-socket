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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import py.com.semp.lib.socket.configuration.SocketConfiguration;
import py.com.semp.lib.socket.configuration.Values;
import py.com.semp.lib.socket.internal.MessageUtil;
import py.com.semp.lib.socket.internal.Messages;
import py.com.semp.lib.utilidades.communication.DefaultDataReader;
import py.com.semp.lib.utilidades.communication.ShutdownHookAction;
import py.com.semp.lib.utilidades.communication.interfaces.DataCommunicator;
import py.com.semp.lib.utilidades.communication.interfaces.DataReader;
import py.com.semp.lib.utilidades.communication.listeners.ConnectionEventListener;
import py.com.semp.lib.utilidades.communication.listeners.DataListener;
import py.com.semp.lib.utilidades.configuration.ConfigurationValues;
import py.com.semp.lib.utilidades.exceptions.CommunicationException;
import py.com.semp.lib.utilidades.exceptions.CommunicationTimeoutException;
import py.com.semp.lib.utilidades.exceptions.ConnectionClosedException;
import py.com.semp.lib.utilidades.log.Logger;
import py.com.semp.lib.utilidades.log.LoggerManager;
import py.com.semp.lib.utilidades.utilities.ArrayUtils;
import py.com.semp.lib.utilidades.utilities.NamedThreadFactory;

public class SocketChannelDriver implements DataCommunicator
{
	//TODO inform para los errores.
	private static final Logger LOGGER = LoggerManager.getLogger(Values.Constants.SOCKET_CONTEXT);
	
	private SocketChannel socketChannel;
	private volatile String stringIdentifier;
	private SocketConfiguration configurationValues;
	private final Thread shutdownHook = new Thread(new ShutdownHookAction(this));
	private final CopyOnWriteArraySet<DataListener> dataListeners = new CopyOnWriteArraySet<>();
	private final CopyOnWriteArraySet<ConnectionEventListener> connectionEventListeners = new CopyOnWriteArraySet<>();
	private final ExecutorService executorService = Executors.newFixedThreadPool(Values.Constants.SOCKET_LISTENERS_THREAD_POOL_SIZE, new NamedThreadFactory("SocketChannelDriverListener"));
	private final ReentrantLock socketLock = new ReentrantLock();
	private volatile boolean shuttingDown = false;
	private ByteBuffer readBuffer;
	private volatile DataReader dataReader;
	
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
			
			ConnectionClosedException exception = new ConnectionClosedException(errorMessage);
			
			this.informOnSendingError(data, exception);
			
			throw exception;
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
				
				ConnectionClosedException exception = new ConnectionClosedException(errorMessage);
				
				this.informOnSendingError(data, exception);
				
				throw exception;
			}
			
			ByteBuffer buffer = ByteBuffer.wrap(data);
			
			LOGGER.debug(String.format("Sending %d bytes (%s): %s", data.length, this.getStringIdentifier(), ArrayUtils.toHexaArrayString(data)));
			
			while(buffer.hasRemaining())
			{
				if(this.shuttingDown)
				{
					String methodName = "byte[] SocketChannelDriver::readData()";
					
					String errorMessage = MessageUtil.getMessage(Messages.TASK_SHUTDOWN_ERROR, methodName);
					
					ConnectionClosedException exception = new ConnectionClosedException(errorMessage);
					
					this.informOnSendingError(data, exception);
					
					throw exception;
				}
				
				if(timeoutNano >= 0 && System.nanoTime() - start >= timeoutNano)
				{
					String errorMessage = MessageUtil.getMessage(Messages.WRITTING_TIMOUT_ERROR, data.length, this.configurationValues.toString());
					
					ConnectionClosedException exception = new ConnectionClosedException(errorMessage);
					
					this.informOnSendingError(data, exception);
					
					throw exception;
				}
				
				int bytesWritten = socketChannel.write(buffer);
				
				if(bytesWritten < 0)
				{
					String errorMessage = MessageUtil.getMessage(Messages.FAILED_TO_SEND_DATA_ERROR, this.getStringIdentifier());
					
					ConnectionClosedException exception = new ConnectionClosedException(errorMessage);
					
					this.informOnSendingError(data, exception);
					
					throw exception;
				}
			}
			
			this.informDataSent(data);
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.FAILED_TO_SEND_DATA_ERROR, this.getStringIdentifier());
			
			ConnectionClosedException exception = new ConnectionClosedException(errorMessage);
			
			this.informOnSendingError(data, exception);
			
			throw exception;
		}
		finally
		{
			this.socketLock.unlock();
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
		
		int bytesRead = 0;
		
		if(this.shuttingDown)
		{
			String methodName = "byte[] SocketChannelDriver::readData()";
			
			String errorMessage = MessageUtil.getMessage(Messages.TASK_SHUTDOWN_ERROR, methodName);
			
			CommunicationException exception = new CommunicationException(errorMessage);
			
			this.informOnReceivingError(exception);
			
			throw exception;
		}
		
		try
		{
			bytesRead = this.socketChannel.read(buffer);
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.FAILED_TO_RECEIVE_DATA_ERROR, this.getStringIdentifier());
			
			CommunicationException exception = new CommunicationException(errorMessage, e);
			
			this.informOnReceivingError(exception);
			
			throw exception;
		}
		
		if(bytesRead == -1)
		{
			String errorMessage = MessageUtil.getMessage(Messages.END_OF_STREAM_REACHED, this.getStringIdentifier());
			
			ConnectionClosedException exception = new ConnectionClosedException(errorMessage);
			
			this.informOnReceivingError(exception);
			
			throw exception;
		}
		
		buffer.flip();
		
		byte[] data = new byte[bytesRead];
		
		buffer.get(data);
		
		if(data.length > 0)
		{
			this.informDataReceived(data);
		}
		
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
				
				CommunicationException exception = new CommunicationException(errorMessage);
				
				this.informOnConnectError(exception);
				
				throw exception;
			}
			
			if(this.socketChannel != null && this.socketChannel.isConnected())
			{
				String errorMessage = MessageUtil.getMessage(Messages.ALREADY_CONNECTED_ERROR, this.getStringIdentifier());
				
				CommunicationException exception = new CommunicationException(errorMessage);
				
				this.informOnConnectError(exception);
				
				throw exception;
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
			String errorMessage = MessageUtil.getMessage(Messages.CONNECTION_ERROR, this.configurationValues.toString());
			
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
						
						String errorMessage = MessageUtil.getMessage(Messages.CONNECTION_ERROR, this.configurationValues.toString());
						
						throw new CommunicationException(errorMessage, e);
					}
				}
			}
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.CONNECTION_ERROR, this.configurationValues.toString());
			
			throw new CommunicationException(errorMessage, e);
		}
	}
	
	@Override
	public SocketChannelDriver connect(ConfigurationValues configurationValues) throws CommunicationException
	{
		try
		{
			this.setConfigurationValues(configurationValues);
		}
		catch(CommunicationException e)
		{
			this.informOnConnectError(e);
			
			throw e;
		}
		
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
			if(this.closeSocketChannel())
			{
				this.informDisconnected();
				this.waitForExecutorServiceShutdown();
			}
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.DISCONNECTION_ERROR, this.getStringIdentifier());
			
			CommunicationException exception = new CommunicationException(errorMessage, e);
			
			this.informOnDisconnectError(exception);
			
			throw exception;
		}
		catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
			
			String methodName = "SocketChannelDriver SocketChannelDriver::disconnect() ";
			String errorMessage = MessageUtil.getMessage(Messages.INTERRUPTED_ERROR, methodName);
			
			CommunicationException exception = new CommunicationException(errorMessage, e);
			
			try
			{
				this.shutdown();
			}
			catch (CommunicationException e1)
			{
				exception.addSuppressed(e1);
			}
			
			throw exception;
		}
		finally
		{
			this.socketLock.unlock();
		}
		
		return this;
	}
	
	private boolean closeSocketChannel() throws IOException
	{
		if(this.socketChannel != null && this.socketChannel.isOpen())
		{
			try
			{
				this.socketChannel.close();
				
				return true;
			}
			finally
			{
				this.removeShutdownHook();
			}
		}
		
		return false;
	}
	
	private void waitForExecutorServiceShutdown() throws InterruptedException
	{
		this.executorService.shutdown();
		
		try
		{
			if(!this.executorService.awaitTermination(Values.Constants.TERMINATION_TIMOUT_MS, TimeUnit.MILLISECONDS))
			{
				this.executorService.shutdownNow();
				
				String methodName = "boolean" + this.executorService.getClass().getName() + "::awaitTermination(long, TimeUnit) ";
				String errorMessage = MessageUtil.getMessage(Messages.TERMINATION_TIMEOUT_ERROR, methodName);
				
				LOGGER.debug(errorMessage);
			}
		}
		catch (InterruptedException e)
		{
			this.executorService.shutdownNow();
			
			throw e;
		}
	}
	
	@Override
	public SocketChannelDriver shutdown() throws CommunicationException
	{
		this.shuttingDown = true;
		
		this.socketLock.lock();
		
		try
		{
			this.executorService.shutdownNow();
			
			this.closeSocketChannel();
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
	
	private void informConnected()
	{
		String methodName = "void SocketChannelDriver::informConnected()";
		
		this.notifyListeners(methodName, this.connectionEventListeners, (listener) ->
		{
			listener.onConnect(Instant.now(), this);
		});
	}
	
	private void informDisconnected()
	{
		String methodName = "void SocketChannelDriver::informDisconnected()";
		
		this.notifyListeners(methodName, this.connectionEventListeners, (listener) ->
		{
			listener.onDisconnect(Instant.now(), this);
		});
	}
	
	private void informDataSent(byte[] data)
	{
		String methodName = "void SocketChannelDriver::informDataSent(byte[])";
		
		this.notifyListeners(methodName, this.dataListeners, (listener) ->
		{
			listener.onDataSent(Instant.now(), this, data);
		});
	}
	
	private void informDataReceived(byte[] data)
	{
		String methodName = "void SocketChannelDriver::informDataReceived(byte[])";
		
		this.notifyListeners(methodName, this.dataListeners, (listener) ->
		{
			listener.onDataReceived(Instant.now(), this, data);
		});
	}
	
	@Override
	public SocketChannelDriver informOnConnectError(Throwable exception)
	{
		String methodName = "void SocketChannelDriver::informOnConnectError(Throwable)";
		
		this.notifyListeners(methodName, this.connectionEventListeners, (listener) ->
		{
			listener.onConnectError(Instant.now(), this, exception);
		});
		
		return this;
	}
	
	@Override
	public SocketChannelDriver informOnDisconnectError(Throwable exception)
	{
		String methodName = "void SocketChannelDriver::informOnDisconnectError(Throwable)";
		
		this.notifyListeners(methodName, this.connectionEventListeners, (listener) ->
		{
			listener.onDisconnectError(Instant.now(), this, exception);
		});
		
		return this;
	}
	
	@Override
	public void informOnSendingError(byte[] data, Throwable exception)
	{
		String methodName = "void SocketChannelDriver::informOnSendingError(byte[], Throwable)";
		
		this.notifyListeners(methodName, this.dataListeners, (listener) ->
		{
			listener.onSendingError(Instant.now(), this, data, exception);
		});
	}
	
	@Override
	public void informOnReceivingError(Throwable exception)
	{
		String methodName = "void SocketChannelDriver::informReceivingError(Throwable)";
		
		this.notifyListeners(methodName, this.dataListeners, (listener) ->
		{
			listener.onReceivingError(Instant.now(), this, exception);
		});
	}
	
	/**
	 * Notifies all listeners of a particular event in an asynchronous manner.
	 *
	 * @param <T> The type of the listeners to be notified.
	 * @param methodName The name of the method from which this is being called, for logging purposes.
	 * @param listeners The set of listeners to notify.
	 * @param notificationTask The consumer task that performs the notification.
	 */
	private <T> void notifyListeners(String methodName, Set<T> listeners, Consumer<T> notificationTask)
	{
		try
		{
			if(this.executorService.isShutdown() || this.executorService.isTerminated())
			{
				String errorMessage = MessageUtil.getMessage(Messages.TASK_SHUTDOWN_ERROR, methodName);
				
				LOGGER.debug(errorMessage);
				
				return;
			}
			
			this.executorService.submit(() ->
			{
				if(this.shuttingDown)
				{
					return;
				}
				
				for(T listener : listeners)
				{
					try
					{
						notificationTask.accept(listener);
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
			String errorMessage = MessageUtil.getMessage(Messages.TASK_SHUTDOWN_ERROR, methodName);
			
			LOGGER.debug(errorMessage, e);
		}
	}
	
	@Override
	public SocketChannelDriver setConfigurationValues(ConfigurationValues configurationValues) throws CommunicationException
	{
		this.checkConfigurationValues(configurationValues);
		
		this.configurationValues = (SocketConfiguration)configurationValues;
		
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
			SocketAddress remoteAddress = null;
			SocketAddress localAddress = null;
			
			try
			{
				remoteAddress = this.getRemoteAddress();
				localAddress = this.getLocalAddress();
			}
			catch(CommunicationException e)
			{
				LOGGER.warning(e);
			}
			
			StringBuilder sb = new StringBuilder();
			
			sb.append("L[").append(getAddressString(localAddress)).append("]");
			sb.append("<->");
			sb.append("R[").append(getAddressString(remoteAddress)).append("]");
			
			this.stringIdentifier = sb.toString();
		}
		
		return this.stringIdentifier;
	}
	
	public SocketAddress getRemoteAddress() throws CommunicationException
	{
		try
		{
			return this.socketChannel.getRemoteAddress();
		}
		catch(IOException e)
		{
			String methodName = "SocketAddress SocketChannelDriver::getRemoteAddress()";
			
			String errorMessage = MessageUtil.getMessage(Messages.UNABLE_TO_OBTAIN_VALUE_ERROR, "remoteAddress", methodName);
			
			throw new CommunicationException(errorMessage, e);
		}
	}
	
	public SocketAddress getLocalAddress() throws CommunicationException
	{
		try
		{
			return this.socketChannel.getLocalAddress();
		}
		catch(IOException e)
		{
			String methodName = "SocketAddress SocketChannelDriver::getLocalAddress()";
			
			String errorMessage = MessageUtil.getMessage(Messages.UNABLE_TO_OBTAIN_VALUE_ERROR, "localAddress", methodName);
			
			throw new CommunicationException(errorMessage, e);
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
	
	@Override
	public Set<ConnectionEventListener> getConnectionEventListeners()
	{
		return this.connectionEventListeners;
	}
	
	@Override
	public Set<DataListener> getDataListeners()
	{
		return this.dataListeners;
	}
	
	@Override
	public boolean isShuttingdown()
	{
		return this.shuttingDown;
	}
	
	@Override
	public DataReader getDataReader()
	{
		if(this.dataReader == null)
		{
			this.socketLock.lock();
			
			try
			{
				if(this.dataReader == null)
				{
					//TODO cambiar de reader.
//					this.dataReader = new SocketChannelDataReader(this);
					this.dataReader = new DefaultDataReader<>(this);
				}
			}
			finally
			{
				this.socketLock.unlock();
			}
		}
		
		return this.dataReader;
	}
}