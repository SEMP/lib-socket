package py.com.semp.lib.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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

public class SocketDriver implements DataCommunicator
{
	private static final String LISTENERS_THREAD_NAME = "SocketDriverListeners";
	private static final Logger LOGGER = LoggerManager.getLogger(Values.Constants.SOCKET_CONTEXT);
	
	private Socket socket;
	private volatile String stringIdentifier;
	private final Thread shutdownHook = new Thread(new ShutdownHookAction(this));
	private SocketConfiguration configurationValues;
	private final CopyOnWriteArraySet<DataListener> dataListeners = new CopyOnWriteArraySet<>();
	private final CopyOnWriteArraySet<ConnectionEventListener> connectionEventListeners = new CopyOnWriteArraySet<>();
	private final ExecutorService executorService = Executors.newFixedThreadPool(Values.Constants.SOCKET_LISTENERS_THREAD_POOL_SIZE, new NamedThreadFactory(LISTENERS_THREAD_NAME));
	private final ReentrantLock socketLock = new ReentrantLock();
	private volatile boolean shuttingDown = false;
	private volatile boolean stopping = false;
	private volatile boolean connected = false;
	private byte[] readBuffer;
	private volatile DataReader dataReader;
	
	public SocketDriver() throws CommunicationException
	{
		this(new Socket());
	}
	
	public SocketDriver(Socket socket) throws CommunicationException
	{
		super();
		
		this.setSocket(socket);
		
		this.addShutdownHook();
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
	 * Sets the internal {@link Socket} to the specified socket.
	 * This method will lock the socket for thread safety during the operation.
	 *
	 * @param socket the {@link Socket} to set.
	 * @throws NullPointerException if the provided socket is null.
	 */
	private void setSocket(Socket socket) throws CommunicationException
	{
		if(socket == null)
		{
			String methodName = "void SocketDriver::setSocket(Socket)";
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName);
			
			throw new NullPointerException(errorMessage);
		}
		
		this.socketLock.lock();
		
		try
		{
			this.socket = socket;
		}
		finally
		{
			this.socketLock.unlock();
		}
	}
	
	/**
	 * Sets the read timeout for the socket to the specified value in milliseconds.
	 * The read timeout is the maximum time the socket will wait for data to be
	 * available to read before throwing a {@link SocketTimeoutException}.
	 *
	 * @param readTimeoutMS the read timeout in milliseconds.
	 * @throws CommunicationException if there is an error setting the timeout on the socket.
	 */
	private void setSoTimeout(int readTimeoutMS) throws CommunicationException
	{
		try
		{
			this.socket.setSoTimeout(readTimeoutMS);
		}
		catch(SocketException e)
		{
			String methodName = "void SocketDriver::setSoTimeout(int)";
			
			String errorMessage = MessageUtil.getMessage(Messages.UNABLE_TO_SET_CONFIGURATION_ERROR, Values.VariableNames.CONNECTION_TIMEOUT_MS, methodName);
			
			throw new CommunicationException(errorMessage, e);
		}
	}
	
	/**
	 * Retrieves the currently set {@link Socket} instance.
	 *
	 * @return the current socket.
	 */
	public Socket getSocket()
	{
		return this.socket;
	}
	
	/**
	 * Retrieves the remote address to which the socket is connected.
	 *
	 * @return the remote socket address.
	 */
	public SocketAddress getRemoteAddress()
	{
		return this.socket.getRemoteSocketAddress();
	}
	
	/**
	 * Retrieves the local address to which the socket is bound.
	 *
	 * @return the local socket address.
	 */
	public SocketAddress getLocalAddress()
	{
		return this.socket.getLocalSocketAddress();
	}
	
	@Override
	public String getStableStringIdentifier()
	{
		return this.stringIdentifier;
	}
	
	@Override
	public String getDynamicStringIdentifier()
	{
		if(this.stringIdentifier == null)
		{
			SocketAddress remoteAddress = this.getRemoteAddress();
			SocketAddress localAddress = this.getLocalAddress();
			
			StringBuilder sb = new StringBuilder();
			
			if(remoteAddress == null || localAddress == null)
			{
				String pendingText = Values.Utilities.Constants.PENDING_VAULE;
				
				String localAddressText = localAddress == null ? pendingText : getAddressString(localAddress);
				String remoteAddressText = remoteAddress == null ? pendingText : getAddressString(remoteAddress);
				
				sb.append("L[").append(localAddressText).append("]");
				sb.append("<->");
				sb.append("R[").append(remoteAddressText).append("]");
				
				return sb.toString();
			}
			
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
	public SocketDriver setConfigurationValues(ConfigurationValues configurationValues) throws CommunicationException
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
			
			throw new CommunicationException(errorMessage.toString());
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
		int readTimeoutMS = this.configurationValues.getValue(Values.VariableNames.READ_TIMEOUT_MS);
		
		if(readTimeoutMS < 0)
		{
			readTimeoutMS = 0;
		}
		
		this.setSoTimeout(readTimeoutMS);
		
		if(!this.isConnected())
		{
			String errorMessage = MessageUtil.getMessage(Messages.SOCKET_CLOSED_OR_NOT_CONNECTED_ERROR, this.getDynamicStringIdentifier());
			
			ConnectionClosedException exception = new ConnectionClosedException(errorMessage);
			
			this.informOnReceivingError(exception);
			
			throw exception;
		}
		
		if(this.shuttingDown)
		{
			String methodName = "byte[] SocketDriver::readData()";
			
			String errorMessage = MessageUtil.getMessage(Messages.TASK_SHUTDOWN_ERROR, methodName);
			
			CommunicationException exception = new CommunicationException(errorMessage);
			
			this.informOnReceivingError(exception);
			
			throw exception;
		}
		
		this.socketLock.lock();
		
		try
		{
			if(!this.isConnected())
			{
				String errorMessage = MessageUtil.getMessage(Messages.SOCKET_CLOSED_OR_NOT_CONNECTED_ERROR, this.getDynamicStringIdentifier());
				
				ConnectionClosedException exception = new ConnectionClosedException(errorMessage);
				
				this.informOnReceivingError(exception);
				
				throw exception;
			}
			
			InputStream inputStream = this.socket.getInputStream();
			
			byte[] dataBuffer = this.getReadBuffer();
			
			int bytesRead = inputStream.read(dataBuffer);;
			
			if(bytesRead == -1)
			{
				String errorMessage = MessageUtil.getMessage(Messages.END_OF_STREAM_REACHED, this.getDynamicStringIdentifier());
				
				ConnectionClosedException exception = new ConnectionClosedException(errorMessage);
				
				this.informOnReceivingError(exception);
				
				throw exception;
			}
			
			if(dataBuffer != null && bytesRead > 0)
			{
				byte[] data = ArrayUtils.subArray(dataBuffer, 0, bytesRead);;
				
				this.informOnDataReceived(data);
				
				return data;
			}
		}
		catch(SocketTimeoutException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.READING_TIMOUT_ERROR, this.getConfigurationValues().toString());
			
			CommunicationTimeoutException exception = new CommunicationTimeoutException(errorMessage, e);
			
			this.informOnReceivingError(e);
			
			throw exception;
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.FAILED_TO_RECEIVE_DATA_ERROR, this.getDynamicStringIdentifier());
			
			CommunicationException exception = new CommunicationException(errorMessage, e);
			
			this.informOnReceivingError(exception);
			
			throw exception;
		}
		catch(CommunicationException e)
		{
			this.informOnReceivingError(e);
			
			throw e;
		}
		finally
		{
			this.socketLock.unlock();
		}
		
		return new byte[]{};
	}
	
	/**
	 * Provides a buffer for reading data from the socket. This method ensures
	 * the buffer is correctly sized as specified by the {@code SOCKET_BUFFER_SIZE_BYTES} configuration value.
	 * If the current buffer is not initialized or is smaller than required, a new buffer is allocated.
	 *
	 * @return a byte array ready for reading data.
	 */
	private byte[] getReadBuffer()
	{
		int bufferSize = this.configurationValues.getValue(Values.VariableNames.SOCKET_BUFFER_SIZE_BYTES);
		
		if(this.readBuffer == null || this.readBuffer.length < bufferSize)
		{
			this.readBuffer = new byte[bufferSize];
		}
		
		return this.readBuffer;
	}
	
	/**
	 * Sends data over the socket to the connected remote server. Before sending data, it checks
	 * if the socket is still connected and sets a write timeout based on the configuration.
	 * If the socket is not connected or data is null or empty, it throws a {@code ConnectionClosedException}.
	 * If the data is sent successfully, it informs the data listeners that data has been sent.
	 * In case of an IOException during sending, it will disconnect the socket and inform the listeners of the error.
	 *
	 * @param data the byte array of data to be sent.
	 * @return the {@code SocketDriver} instance to allow for method chaining.
	 * @throws CommunicationException if there is a problem with sending the data.
	 */
	@Override
	public SocketDriver sendData(byte[] data) throws CommunicationException
	{
		if(!this.isConnected())
		{
			String errorMessage = MessageUtil.getMessage(Messages.SOCKET_CLOSED_OR_NOT_CONNECTED_ERROR, this.getDynamicStringIdentifier());
			
			ConnectionClosedException exception = new ConnectionClosedException(errorMessage);
			
			this.informOnSendingError(data, exception);
			
			throw exception;
		}
		
		if(data == null || data.length == 0)
		{
			return this;
		}
		
		this.socketLock.lock();
		
		try
		{
			if(!this.isConnected())
			{
				String errorMessage = MessageUtil.getMessage(Messages.SOCKET_CLOSED_OR_NOT_CONNECTED_ERROR, this.getDynamicStringIdentifier());
				
				ConnectionClosedException exception = new ConnectionClosedException(errorMessage);
				
				this.informOnSendingError(data, exception);
				
				throw exception;
			}
			
			OutputStream outputStream = this.socket.getOutputStream();
			
			if(LOGGER.isDebugging())
			{
				String message = MessageUtil.getMessage(Messages.SENDING_DEBUG_MESSAGE, data.length, this.getDynamicStringIdentifier(), new String(data), ArrayUtils.toHexaArrayString(data));
				
				LOGGER.debug(message);
			}
			
			outputStream.write(data);
			outputStream.flush();
			
			this.informOnDataSent(data);
		}
		catch(IOException e)
		{
			this.stopping = true;
			
			String errorMessage = MessageUtil.getMessage(Messages.FAILED_TO_SEND_DATA_ERROR, this.getDynamicStringIdentifier());
			
			ConnectionClosedException exception = new ConnectionClosedException(errorMessage, e);
			
			this.informOnSendingError(data, exception);
			
			throw exception;
		}
		finally
		{
			this.socketLock.unlock();
		}
		
		return this;
	}
	
	@Override
	public SocketDriver connect() throws CommunicationException
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
			
			if(this.socket != null && this.socket.isConnected())
			{
				String errorMessage = MessageUtil.getMessage(Messages.ALREADY_CONNECTED_ERROR, this.getDynamicStringIdentifier());
				
				CommunicationException exception = new CommunicationException(errorMessage);
				
				this.informOnConnectError(exception);
				
				throw exception;
			}
			
			if(this.socket == null)
			{
				this.socket = new Socket();
			}
			
			String address = this.configurationValues.getValue(Values.VariableNames.REMOTE_ADDRESS);
			int port = this.configurationValues.getValue(Values.VariableNames.REMOTE_PORT);
			int connectionTimeoutMS = this.configurationValues.getValue(Values.VariableNames.CONNECTION_TIMEOUT_MS);
			
			this.waitConnection(new InetSocketAddress(address, port), connectionTimeoutMS);
			
			this.getDynamicStringIdentifier();
			
			this.informOnConnect();
		}
		catch(CommunicationException e)
		{
			this.informOnConnectError(e);
			
			this.stopping = true;
			
			throw e;
		}
		finally
		{
			this.socketLock.unlock();
		}
		
		return this;
	}
	
	@Override
	public SocketDriver connect(ConfigurationValues configurationValues) throws CommunicationException
	{
		try
		{
			this.setConfigurationValues(configurationValues);
		}
		catch(CommunicationException e)
		{
			this.informOnConnectError(e);
			
			this.disconnect();
			
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
	public SocketDriver connect(String address, int port) throws CommunicationException
	{
		SocketConfiguration configurationValues = new SocketConfiguration();
		
		configurationValues.setParameter(Values.VariableNames.REMOTE_ADDRESS, address);
		configurationValues.setParameter(Values.VariableNames.REMOTE_PORT, port);
		
		return this.connect(configurationValues);
	}
	
	/**
	 * Attempts to establish a connection to the provided remote address within the specified timeout.
	 * If the timeout is negative, it is treated as zero, which implies an infinite timeout. In the case of
	 * a timeout, a {@link CommunicationTimeoutException} is thrown. For other I/O issues during the connection
	 * attempt, a {@link CommunicationException} is thrown.
	 *
	 * @param address the remote address to connect to
	 * @param connectionTimeoutMS the timeout for the connection attempt in milliseconds
	 *  a value of zero implies an infinite timeout.
	 * @throws CommunicationTimeoutException if the connection attempt times out
	 * @throws CommunicationException if the connection attempt is interrupted by an I/O error
	 */
	private void waitConnection(InetSocketAddress address, int connectionTimeoutMS) throws CommunicationException
	{
		try
		{
			if(connectionTimeoutMS < 0)
			{
				connectionTimeoutMS = 0;
			}
			
			this.socket.connect(address, connectionTimeoutMS);
			
			this.connected = true;
		}
		catch(SocketTimeoutException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.CONNECTING_TIMOUT_ERROR, this.configurationValues.toString());
			
			throw new CommunicationTimeoutException(errorMessage);
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.CONNECTION_ERROR, this.configurationValues.toString());
			
			throw new CommunicationException(errorMessage, e);
		}
	}
	
	@Override
	public SocketDriver disconnect() throws CommunicationException
	{
		this.socketLock.lock();
		
		try
		{
			boolean socketClosed = false;
			
			try
			{
				socketClosed = this.closeSocket();
			}
			finally
			{
				this.stopping = true;
			}
			
			if(socketClosed)
			{
				this.informOnDisconnect();
			}
			
			this.shutdownAndWaitForExecutorService();
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.DISCONNECTION_ERROR, this.getDynamicStringIdentifier());
			
			CommunicationException exception = new CommunicationException(errorMessage, e);
			
			this.informOnDisconnectError(exception);
			
			throw exception;
		}
		catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
			
			String methodName = "SocketDriver SocketDriver::disconnect() ";
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
	public SocketDriver shutdown() throws CommunicationException
	{
		this.shuttingDown = true;
		this.connected = false;
		
		this.socketLock.lock();
		
		try
		{
			this.executorService.shutdownNow();
			
			this.closeSocket();
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.SHUTDOWN_ERROR, this.getDynamicStringIdentifier());
			
			throw new CommunicationException(errorMessage, e);
		}
		finally
		{
			this.socketLock.unlock();
		}
		
		return this;
	}
	
	/**
	 * Closes the socket if it is open. This method will also remove any shutdown hook associated with
	 * the socket channel after attempting to close it. This is a cleanup method typically called when shutting down
	 * the connection to the remote server.
	 *
	 * @return {@code true} if the socket channel was open and is now closed, {@code false} otherwise.
	 * @throws IOException if an I/O error occurs when closing the socket.
	 */
	private boolean closeSocket() throws IOException
	{
		if(this.isConnected())
		{
			this.connected = false;
			
			IOException suppressedException = null;
			
			try
			{
				try
				{
					this.socket.shutdownOutput();
					this.socket.shutdownInput();
				}
				catch(IOException e)
				{
					suppressedException = e;
				}
				
				this.socket.close();
			}
			catch(IOException e)
			{
				if(suppressedException != null)
				{
					e.addSuppressed(suppressedException);
				}
				
				throw e;
			}
			finally
			{
				this.removeShutdownHook();
			}
			
			if(suppressedException != null)
			{
				throw suppressedException;
			}
			
			return true;
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
	private void shutdownAndWaitForExecutorService() throws InterruptedException
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
	 * of the {@code SocketDriver} to each listener's {@code onConnect} method.
	 */
	private void informOnConnect()
	{
		String methodName = "void SocketDriver::informConnected()";
		
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
		String methodName = "void SocketDriver::informDisconnected()";
		
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
		String methodName = "void SocketDriver::informDataSent(byte[])";
		
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
		String methodName = "void SocketDriver::informDataReceived(byte[])";
		
		this.notifyListeners(methodName, this.dataListeners, (listener) ->
		{
			listener.onDataReceived(Instant.now(), this, data);
		});
	}
	
	@Override
	public SocketDriver informOnConnectError(Throwable exception)
	{
		String methodName = "SocketDriver SocketDriver::informOnConnectError(Throwable)";
		
		this.notifyListeners(methodName, this.connectionEventListeners, (listener) ->
		{
			listener.onConnectError(Instant.now(), this, exception);
		});
		
		return this;
	}
	
	@Override
	public SocketDriver informOnDisconnectError(Throwable exception)
	{
		String methodName = "SocketDriver SocketDriver::informOnDisconnectError(Throwable)";
		
		this.notifyListeners(methodName, this.connectionEventListeners, (listener) ->
		{
			listener.onDisconnectError(Instant.now(), this, exception);
		});
		
		return this;
	}
	
	@Override
	public SocketDriver informOnSendingError(byte[] data, Throwable exception)
	{
		String methodName = "SocketDriver SocketChannelDriver::informOnSendingError(byte[], Throwable)";
		
		this.notifyListeners(methodName, this.dataListeners, (listener) ->
		{
			listener.onSendingError(Instant.now(), this, data, exception);
		});
		
		return this;
	}
	
	@Override
	public SocketDriver informOnReceivingError(Throwable exception)
	{
		String methodName = "SocketDriver SocketDriver::informOnReceivingError(Throwable)";
		
		this.notifyListeners(methodName, this.dataListeners, (listener) ->
		{
			listener.onReceivingError(Instant.now(), this, exception);
		});
		
		return this;
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
	public SocketDriver addConnectionEventListeners(ConnectionEventListener... listeners)
	{
		if(listeners == null)
		{
			String methodName = "SocketDriver SocketDriver::removeConnectionEveaddConnectionEventListeners(ConnectionEventListener...)";
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName);
			
			throw new NullPointerException(errorMessage);
		}
		
		this.connectionEventListeners.addAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SocketDriver removeConnectionEventListeners(ConnectionEventListener... listeners)
	{
		if(listeners == null)
		{
			String methodName = "SocketDriver SocketDriver::removeConnectionEventListeners(ConnectionEventListener...)";
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName);
			
			throw new NullPointerException(errorMessage);
		}
		
		this.connectionEventListeners.removeAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SocketDriver removeAllConnectionEventListeners()
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
	public SocketDriver addDataListeners(DataListener... listeners)
	{
		if(listeners == null)
		{
			String methodName = "SocketDriver SocketDriver::addDataListeners(DataListener...)";
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName);
			
			throw new NullPointerException(errorMessage);
		}
		
		this.dataListeners.addAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SocketDriver removeDataListeners(DataListener... listeners)
	{
		if(listeners == null)
		{
			String methodName = "SocketDriver SocketDriver::removeDataListeners(DataListener...)";
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName);
			
			throw new NullPointerException(errorMessage);
		}
		
		this.dataListeners.removeAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SocketDriver removeAllDataListeners()
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
		if(this.socket == null)
		{
			return false;
		}
		
		boolean socketConnected = this.socket.isConnected() && !this.socket.isClosed();
		
		return !this.stopping && this.connected && socketConnected;
	}
	
	@Override
	public boolean isStopping()
	{
		return this.stopping;
	}
	
	@Override
	public boolean isShuttingdown()
	{
		return this.shuttingDown;
	}
}