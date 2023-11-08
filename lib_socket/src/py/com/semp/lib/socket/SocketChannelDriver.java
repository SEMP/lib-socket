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
import py.com.semp.lib.utilidades.communication.interfaces.DataInterface;
import py.com.semp.lib.utilidades.communication.interfaces.DataReader;
import py.com.semp.lib.utilidades.communication.interfaces.DataReceiver;
import py.com.semp.lib.utilidades.communication.interfaces.DataTransmitter;
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

/**
 * Implements the {@link DataCommunicator} interface using a {@link SocketChannel} to
 * provide non-blocking IO operations for sending and receiving data over TCP/IP.
 * <p>
 * This class manages the underlying socket channel's connection lifecycle and provides
 * methods to read and write data asynchronously. It supports adding {@link DataListener}s
 * to notify about data sending and receiving events and any associated errors.
 * </p>
 *
 * <p>Usage Example:</p>
 * <pre>
 * SocketChannelDriver driver = new SocketChannelDriver();
 * driver.connect("127.0.0.1", 8080);
 * driver.sendData("Message");
 * ...
 * driver.disconnect();
 * </pre>
 *
 * @see DataCommunicator
 * @see DataTransmitter
 * @see DataInterface
 * @see DataReceiver
 * @see DataReader
 * @see DataListener
 * @see ConnectionEventListener
 * @see SocketChannel
 */
public class SocketChannelDriver implements DataCommunicator
{
	private static final String LISTENERS_THREAD_NAME = "SocketChannelDriverListeners";
	private static final Logger LOGGER = LoggerManager.getLogger(Values.Constants.SOCKET_CONTEXT);
	
	private SocketChannel socketChannel;
	private volatile String stringIdentifier;
	private final Thread shutdownHook = new Thread(new ShutdownHookAction(this));
	private SocketConfiguration configurationValues;
	private final CopyOnWriteArraySet<DataListener> dataListeners = new CopyOnWriteArraySet<>();
	private final CopyOnWriteArraySet<ConnectionEventListener> connectionEventListeners = new CopyOnWriteArraySet<>();
	private final ExecutorService executorService = Executors.newFixedThreadPool(Values.Constants.SOCKET_LISTENERS_THREAD_POOL_SIZE, new NamedThreadFactory(LISTENERS_THREAD_NAME));
	private final ReentrantLock socketLock = new ReentrantLock();
	private volatile boolean shuttingDown = false;
	private ByteBuffer readBuffer;
	private volatile DataReader dataReader;
	
	/**
	 * Constructs a new {@code SocketChannelDriver} instance by opening a new socket channel.
	 * The new socket channel is obtained through {@code getNewSocketChannel()}.
	 *
	 * @throws CommunicationException if there is an I/O error when opening the socket channel.
	 */
	public SocketChannelDriver() throws CommunicationException
	{
		this(getNewSocketChannel());
	}
	
	/**
	 * Constructs a new {@code SocketChannelDriver} instance with the provided socket channel.
	 * Also, adds a JVM shutdown hook to ensure the channel is closed on program exit.
	 *
	 * @param socketChannel the {@link SocketChannel} to use for communication.
	 * @throws NullPointerException if the socket channel provided is null.
	 */
	public SocketChannelDriver(SocketChannel socketChannel)
	{
		super();
		
		this.setSocketChannel(socketChannel);
		
		this.addShutdownHook();
	}
	
	/**
	 * Opens a new socket channel.
	 *
	 * @return A new {@link SocketChannel} instance.
	 * @throws CommunicationException if an I/O error occurs when opening the socket channel.
	 */
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
	
	/**
	 * Adds a JVM shutdown hook that is called when the JVM is shutting down.
	 * This hook will typically be used to close the socket channel to ensure all resources are freed.
	 */
	private void addShutdownHook()
	{
		Runtime.getRuntime().addShutdownHook(this.shutdownHook);
	}
	
	/**
	 * Removes the JVM shutdown hook that was added by {@code addShutdownHook()}.
	 * This method is typically called to prevent the hook from running if the socket channel
	 * has already been closed and resources have been freed.
	 */
	private void removeShutdownHook()
	{
		Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
	}
	
	/**
	 * Sets the internal {@link SocketChannel} to the specified channel.
	 * This method will lock the socket for thread safety during the operation.
	 *
	 * @param socketChannel the {@link SocketChannel} to set.
	 * @throws NullPointerException if the provided socket channel is null.
	 */
	private void setSocketChannel(SocketChannel socketChannel)
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
	
	/**
	 * Retrieves the associated {@link SocketChannel} for this driver.
	 *
	 * @return the underlying {@link SocketChannel}.
	 */
	
	public SocketChannel getSocketChannel()
	{
		return this.socketChannel;
	}
	
	/**
	 * Obtains the remote address to which the socket channel is connected.
	 *
	 * @return the remote address of the socket channel.
	 * @throws CommunicationException if an I/O error occurs when attempting to get the remote address.
	 */
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
	
	/**
	 * Obtains the local address of the socket channel.
	 *
	 * @return the local address of the socket channel.
	 * @throws CommunicationException if an I/O error occurs when attempting to get the local address.
	 */
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
	
	/**
	 * Converts a {@link SocketAddress} into a human-readable string format.
	 * If the address is an {@code InetSocketAddress}, it will return a string in the form of 'IP:Port'.
	 * Otherwise, it will invoke {@code toString()} on the address.
	 *
	 * @param socketAddress the {@link SocketAddress} to be converted into a string.
	 * @return a string representation of the provided {@code SocketAddress}.
	 */
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
	
	/**
	 * Retrieves a configuration value for a given name. If the value is not set, it returns the provided default value.
	 * This method uses the configuration values from the data receiver to get the setting.
	 *
	 * @param name the name of the configuration to retrieve
	 * @param defaultValue the default value to return if the configuration is not set
	 * @return the value of the configuration, or the default value if not set
	 */
	private <T> T getConfiguration(String name, T defaultValue)
	{
		ConfigurationValues configurationValues = this.getConfigurationValues();
		
		if(configurationValues == null)
		{
			return defaultValue;
		}
		
		return configurationValues.getValue(name, defaultValue);
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
		
		this.socketLock.lock();
		
		try
		{
			bytesRead = this.socketChannel.read(buffer);
			
			if(bytesRead == -1)
			{
				String errorMessage = MessageUtil.getMessage(Messages.END_OF_STREAM_REACHED, this.getStringIdentifier());
				
				ConnectionClosedException exception = new ConnectionClosedException(errorMessage);
				
				this.informOnReceivingError(exception);
				
				throw exception;
			}
			
			if(bytesRead > 0)
			{
				buffer.flip();
				
				byte[] data = new byte[bytesRead];
				
				buffer.get(data);
				
				this.informOnDataReceived(data);
				
				return data;
			}
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.FAILED_TO_RECEIVE_DATA_ERROR, this.getStringIdentifier());
			
			CommunicationException exception = new CommunicationException(errorMessage, e);
			
			this.informOnReceivingError(exception);
			
			throw exception;
		}
		finally
		{
			this.socketLock.unlock();
		}
		
		return new byte[]{};
	}
	
	/**
	 * Provides a {@link ByteBuffer} for reading data from the socket channel. This method ensures
	 * the buffer is correctly sized as specified by the {@code SOCKET_BUFFER_SIZE_BYTES} configuration value.
	 * If the current buffer is not initialized or is smaller than required, a new buffer is allocated.
	 *
	 * @return a {@link ByteBuffer} ready for reading data.
	 */
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
			
			this.informOnDataSent(data);
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
			int connectionTimeoutMS = this.configurationValues.getValue(Values.VariableNames.CONNECTION_TIMEOUT_MS);
			long connectionTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(connectionTimeoutMS);
			
			this.socketChannel.configureBlocking(false);
			
			this.waitConnection(new InetSocketAddress(address, port), connectionTimeoutNanos);
			
			this.getStringIdentifier();
			
			this.informOnConnect();
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.CONNECTION_ERROR, this.configurationValues.toString());
			
			CommunicationException exception = new CommunicationException(errorMessage, e);
			
			this.informOnConnectError(exception);
			
			throw exception;
		}
		finally
		{
			this.socketLock.unlock();
		}
		
		return this;
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
	
	/**
	 * Attempts to connect to the specified address and port using a new {@link SocketConfiguration}.
	 * The method configures the remote address and port to connect to, and delegates the actual
	 * connection process to the {@code connect} method that takes a {@link ConfigurationValues} parameter.
	 *
	 * @param address the IP address of the remote server to connect to
	 * @param port the port number of the remote server to connect to
	 * @return the {@code SocketChannelDriver} instance, connected to the specified address and port
	 * @throws CommunicationException if any connection error occurs, such as an I/O error or a shutdown signal
	 */
	public SocketChannelDriver connect(String address, int port) throws CommunicationException
	{
		SocketConfiguration configurationValues = new SocketConfiguration();
		
		configurationValues.setParameter(Values.VariableNames.REMOTE_ADDRESS, address);
		configurationValues.setParameter(Values.VariableNames.REMOTE_PORT, port);
		
		return this.connect(configurationValues);
	}
	
	/**
	 * Waits for a connection to be established with a remote address within a certain timeout.
	 * This method initiates a non-blocking connect operation and periodically checks if the
	 * connection has been established, or if the connection attempt has been interrupted or timed out.
	 * 
	 * @param address the remote address to connect to
	 * @param connectionTimeoutNano the timeout for the connection attempt, in nanoseconds
	 * @throws CommunicationException if the connection attempt is interrupted by a shutdown signal
	 * @throws CommunicationTimeoutException if the connection attempt times out
	 */
	private void waitConnection(InetSocketAddress address, long connectionTimeoutNano) throws CommunicationException
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
					
					if(connectionTimeoutNano >= 0 && System.nanoTime() - start >= connectionTimeoutNano)
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
	public SocketChannelDriver disconnect() throws CommunicationException
	{
		this.socketLock.lock();
		
		try
		{
			if(this.closeSocketChannel())
			{
				this.informOnDisconnect();
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
			catch(CommunicationException e1)
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
	
	/**
	 * Closes the socket channel if it is open. This method will also remove any shutdown hook associated with
	 * the socket channel after attempting to close it. This is a cleanup method typically called when shutting down
	 * the connection to the remote server.
	 *
	 * @return {@code true} if the socket channel was open and is now closed, {@code false} otherwise.
	 * @throws IOException if an I/O error occurs when closing the socket channel.
	 */
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
	
	/**
	 * Initiates an orderly shutdown of the executor service and waits for its termination. The method retrieves
	 * the termination timeout dynamically from configuration. If the executor service does not terminate within the
	 * specified timeout, it is forcibly shut down. A debug message is logged if the termination timeout is exceeded.
	 *
	 * The termination timeout is obtained from the configuration values using {@code Values.VariableNames.TERMINATION_TIMOUT_MS},
	 * with a default specified by {@code Values.Defaults.TERMINATION_TIMOUT_MS} if not configured.
	 *
	 * @throws InterruptedException if the current thread is interrupted while waiting, in which case
	 *         the executor service is also shut down immediately
	 */
	private void waitForExecutorServiceShutdown() throws InterruptedException
	{
		this.executorService.shutdown();
		
		try
		{
			Integer terminationTimeoutMS = this.getConfiguration(Values.VariableNames.TERMINATION_TIMOUT_MS, Values.Defaults.TERMINATION_TIMOUT_MS);
			
			if(!this.executorService.awaitTermination(terminationTimeoutMS, TimeUnit.MILLISECONDS))
			{
				this.executorService.shutdownNow();
				
				String methodName = "boolean" + this.executorService.getClass().getName() + "::awaitTermination(long, TimeUnit) ";
				String errorMessage = MessageUtil.getMessage(Messages.TERMINATION_TIMEOUT_ERROR, methodName);
				
				LOGGER.debug(errorMessage);
			}
		}
		catch(InterruptedException e)
		{
			this.executorService.shutdownNow();
			
			throw e;
		}
	}
	
	/**
	 * Notifies all registered connection event listeners about the successful establishment of a connection.
	 * This method captures the current instant of the notification and passes it along with the instance
	 * of the {@code SocketChannelDriver} to each listener's {@code onConnect} method.
	 */
	private void informOnConnect()
	{
		String methodName = "void SocketChannelDriver::informConnected()";
		
		this.notifyListeners(methodName, this.connectionEventListeners, (listener) ->
		{
			listener.onConnect(Instant.now(), this);
		});
	}
	
	/**
	 * Notifies all registered connection event listeners about the disconnection.
	 * This method captures the current instant of the notification and passes it
	 * along with the instance of the {@code SocketChannelDriver} to each listener's
	 * {@code onDisconnect} method.
	 */
	private void informOnDisconnect()
	{
		String methodName = "void SocketChannelDriver::informDisconnected()";
		
		this.notifyListeners(methodName, this.connectionEventListeners, (listener) ->
		{
			listener.onDisconnect(Instant.now(), this);
		});
	}
	
	/**
	 * Notifies all registered data event listeners about the successful sending of data.
	 * This method captures the current instant of the notification, along with the byte array of data sent,
	 * and passes these details to each listener's {@code onDataSent} method.
	 *
	 * @param data the array of bytes that was sent
	 */
	private void informOnDataSent(byte[] data)
	{
		String methodName = "void SocketChannelDriver::informDataSent(byte[])";
		
		this.notifyListeners(methodName, this.dataListeners, (listener) ->
		{
			listener.onDataSent(Instant.now(), this, data);
		});
	}
	
	/**
	 * Notifies all registered data event listeners about the successful reception of data.
	 * This method captures the current instant of the notification, along with the byte array of data received,
	 * and passes these details to each listener's {@code onDataReceived} method.
	 *
	 * @param data the array of bytes that was received
	 */
	private void informOnDataReceived(byte[] data)
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
	public Set<ConnectionEventListener> getConnectionEventListeners()
	{
		return this.connectionEventListeners;
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
	public Set<DataListener> getDataListeners()
	{
		return this.dataListeners;
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
	public boolean isShuttingdown()
	{
		return this.shuttingDown;
	}
}