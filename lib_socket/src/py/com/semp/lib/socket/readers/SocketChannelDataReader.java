package py.com.semp.lib.socket.readers;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import py.com.semp.lib.socket.SocketChannelDriver;
import py.com.semp.lib.socket.SocketDriver;
import py.com.semp.lib.socket.internal.MessageUtil;
import py.com.semp.lib.socket.internal.Messages;
import py.com.semp.lib.utilidades.communication.interfaces.DataInterface;
import py.com.semp.lib.utilidades.communication.interfaces.DataReader;
import py.com.semp.lib.utilidades.communication.listeners.ConnectionEventListener;
import py.com.semp.lib.utilidades.configuration.ConfigurationValues;
import py.com.semp.lib.utilidades.configuration.Values;
import py.com.semp.lib.utilidades.exceptions.CommunicationException;
import py.com.semp.lib.utilidades.exceptions.CommunicationTimeoutException;
import py.com.semp.lib.utilidades.log.Logger;
import py.com.semp.lib.utilidades.log.LoggerManager;

public class SocketChannelDataReader implements DataReader, ConnectionEventListener
{
	private Selector selector;
	private Map<SocketChannel, SocketChannelDriver> channelMap = new ConcurrentHashMap<>();
	
	private volatile boolean pauseReading = false;
	private volatile boolean reading = false;
	private volatile boolean readingComplete = false;
	private volatile boolean stopping = false;
	private volatile boolean shuttingDown = false;
	private volatile AtomicBoolean threadNameUpdated = new AtomicBoolean(false);
	private final ReentrantLock lock = new ReentrantLock();
	
	private static final Logger LOGGER = LoggerManager.getLogger(Values.Constants.UTILITIES_CONTEXT);
	
	public SocketChannelDataReader()
	{
		super();
	}
	
	public SocketChannelDataReader(SocketChannelDriver socketChannelDriver)
	{
		this();
		
		this.addSocketChannelDriver(socketChannelDriver);
	}

	private void addSocketChannelDriver(SocketChannelDriver socketChannelDriver)
	{
		if(this.shuttingDown || this.stopping)
		{
			return;
		}
		
		socketChannelDriver.addConnectionEventListeners(this);
		
		this.threadNameUpdated.set(false);
		
		SocketChannel socketChannel = socketChannelDriver.getSocketChannel();
		
		this.channelMap.put(socketChannel, socketChannelDriver);
	}
	
	/**
	 * Retrieves the {@link Selector} associated with this data reader.
	 * If the selector is not already initialized, this method will initialize it in a thread-safe manner.
	 * <p>
	 * This method employs a double-checked locking pattern to ensure that the selector is initialized only once,
	 * even when accessed by multiple threads concurrently. The selector is created using {@link Selector#open()},
	 * and any {@link IOException} encountered during its creation is wrapped in a {@link CommunicationException}.
	 * </p>
	 *
	 * @return The existing or newly created {@link Selector} for this data reader.
	 * @throws CommunicationException if an error occurs while opening the selector. This exception wraps
	 * the underlying {@link IOException} and includes additional context about the error location.
	 */
	public Selector getSelector() throws CommunicationException
	{
		if(this.selector == null)
		{
			this.lock.lock();
			
			try
			{
				if(this.selector == null)
				{
					this.selector = Selector.open();
				}
			}
			catch(IOException e)
			{
				String methodName = "Selector SocketChannelDataReader::getSelector()";
				
				String errorMessage = MessageUtil.getMessage(Messages.OPENING_SELECTOR_ERROR, methodName);
				
				throw new CommunicationException(errorMessage, e);
			}
			finally
			{
				this.lock.unlock();
			}
		}
		
		return this.selector;
	}
	
	/**
	 * Main reading loop that runs asynchronously to fetch data from the receiver.
	 */
	@Override
	public void run()
	{
		this.setThreadName();
		
		while(true)
		{
			if(this.shuttingDown)
			{
				return;
			}
			
			if(this.stopping)
			{
				break;
			}
			
			if(!this.pauseReading)
			{
				if(this.threadNameUpdated.compareAndSet(false, true))
				{
					this.setThreadName();
				}
				
				try
				{
					this.reading = true;
					
					this.readWithTimeout();
				}
				catch(CommunicationException e)
				{
					this.stopReading();
				}
			}
			else
			{
				this.reading = false;
			}
		}
		
		this.completeReading();
	}
	
	private void completeReading()
	{
		try
		{
			for(SocketChannelDriver socketChannelDriver : this.channelMap.values())
			{
				try
				{
					socketChannelDriver.disconnect();
					
				}
				catch(CommunicationException e)
				{
					String errorMessage = MessageUtil.getMessage(Messages.DISCONNECTION_ERROR, this.getReceiverString(socketChannelDriver));
					
					LOGGER.error(errorMessage, e);
				}
			}
			
			Selector selector = null;
			
			try
			{
				selector = this.getSelector();
				
				String methodName = "void SocketChannelDataReader::completeReading()";
				
				this.closeSelector(selector, methodName);
			}
			catch(CommunicationException e)
			{
				LOGGER.error(e);
			}
		}
		finally
		{
			this.reading = false;
			this.readingComplete = true;
		}
	}
	
//	/**
//	 * Attempts to read data with a specified timeout. If data is not received within the timeout period, 
//	 * a CommunicationTimeoutException is thrown. This method repeatedly tries to read data until it is successful 
//	 * or the timeout is exceeded.
//	 *
//	 * @param readTimeoutMS the maximum time in miliseconds to wait for data to be read
//	 * @return the read data as a byte array
//	 * @throws CommunicationException if there is an issue with reading the data or a timeout occurs
//	 */
	private byte[] readWithTimeout() throws CommunicationException
	{
		//TODO cuidar shutdown y stop y timeouts
		byte[] data;
		
		Selector selector = this.getSelector();
		
		do
		{
			int readyChannels = selector.select(readTimeoutMS);
			
			if(readyChannels == 0)
			{
				String errorMessage = MessageUtil.getMessage(Messages.READING_TIMOUT_ERROR, this.getConfigurationValues().toString());
				
				CommunicationTimeoutException exception = new CommunicationTimeoutException(errorMessage);
				
				this.dataReceiver.informOnReceivingError(exception);
				
				continue;
			}
			
			Set<SelectionKey> selectedKeys = selector.selectedKeys();
			
			for(SelectionKey key : selectedKeys)
			{
				
			}
			
			data = this.dataReceiver.readData();
			
		}
		while(data.length == 0);
		
		return data;
	}
	
	private void setThreadName()
	{
		Thread currentThread = Thread.currentThread();
		
		StringBuilder threadName = new StringBuilder();
		
		threadName.append(this.getClass().getSimpleName());
		threadName.append("_");
		threadName.append(currentThread.getId());
		threadName.append("_");
		
		int numberConnections = this.channelMap.size();
		
		if(numberConnections == 1)
		{
			Collection<SocketChannelDriver> values = channelMap.values();
			
			SocketChannelDriver socketChannelDriver = values.iterator().next();
			
			threadName.append(socketChannelDriver.getDynamicStringIdentifier());
		}
		else
		{
			threadName.append("connections(");
			threadName.append(numberConnections);
			threadName.append(")");
		}
		
		currentThread.setName(threadName.toString());
	}
	
	@Override
	public void startReading()
	{
		this.pauseReading = false;
	}
	
	@Override
	public void pauseReading()
	{
		this.pauseReading = true;
	}
	
	@Override
	public void stopReading()
	{
		this.stopping = true;
	}
	
	@Override
	public boolean isReading()
	{
		return this.reading;
	}
	
	@Override
	public boolean isReadingComplete()
	{
		return this.readingComplete;
	}
	
	@Override
	public void shutdown()
	{
		this.shuttingDown = true;
		
		Selector selector = null;
		
		try
		{
			selector = this.getSelector();
			
			selector.wakeup();
		}
		catch(CommunicationException e)
		{
			String methodName = "void SocketChannelDataReader::shutdown()";
			String errorMessage = MessageUtil.getMessage(Messages.SHUTDOWN_ERROR, methodName);
			
			LOGGER.error(errorMessage, e);
		}
		
		for(SocketChannelDriver socketChannelDriver : this.channelMap.values())
		{
			this.unregisterSelector(selector, socketChannelDriver);
			
			try
			{
				socketChannelDriver.shutdown();
			}
			catch(CommunicationException e)
			{
				String methodName = "void SocketChannelDataReader::shutdown()";
				String errorMessage = MessageUtil.getMessage(Messages.SHUTDOWN_ERROR, methodName);
				
				LOGGER.error(errorMessage, e);
			}
		}
		
		String methodName = "void SocketChannelDataReader::shutdown()";
		
		try
		{
			this.closeSelector(selector, methodName);
		}
		catch(CommunicationException e)
		{
			LOGGER.error(e);
		}
	}
	
	private void closeSelector(Selector selector, String methodName) throws CommunicationException
	{
		if(selector != null)
		{
			try
			{
				selector.close();
			}
			catch(IOException e)
			{
				String errorMessage = MessageUtil.getMessage(Messages.SHUTDOWN_ERROR, methodName);
				
				throw new CommunicationException(errorMessage, e);
			}
		}
	}
	
	@Override
	public void onDisconnect(Instant instant, DataInterface dataInterface)
	{
		if(this.shuttingDown)
		{
			return;
		}
		
		if(dataInterface instanceof SocketChannelDriver)
		{
			SocketChannelDriver socketChannelDriver = (SocketChannelDriver)dataInterface;
			
			this.threadNameUpdated.set(false);
			
			this.channelMap.remove(socketChannelDriver.getSocketChannel());
			
			try
			{
				this.unregisterSelector(socketChannelDriver);
			}
			catch(CommunicationException e)
			{
				LOGGER.error(e);
			}
		}
	}
	
	/**
	 * Unregisters the given {@link SocketChannelDriver}'s socket channel from its associated selector.
	 * This method retrieves the current selector and proceeds to unregister the socket channel, 
	 * typically in response to a disconnection or during the shutdown process.
	 * 
	 * @param socketChannelDriver The {@link SocketChannelDriver} whose socket channel is to be unregistered.
	 * @throws CommunicationException if an error occurs while retrieving the selector.
	 */
	private void unregisterSelector(SocketChannelDriver socketChannelDriver) throws CommunicationException
	{
		Selector selector = this.getSelector();
		
		this.unregisterSelector(selector, socketChannelDriver);
	}
	
	/**
	 * Unregisters the given {@link SocketChannelDriver}'s socket channel from the specified selector.
	 * This method is used to remove the socket channel from the selector's monitoring, typically during a shutdown or disconnection.
	 * 
	 * @param selector The {@link Selector} with which the socket channel is registered.
	 * @param socketChannelDriver The {@link SocketChannelDriver} whose socket channel is to be unregistered.
	 */
	private void unregisterSelector(Selector selector, SocketChannelDriver socketChannelDriver)
	{
		SocketChannel socketChannel = socketChannelDriver.getSocketChannel();
		
		if(selector != null)
		{
			SelectionKey selectorKey = socketChannel.keyFor(selector);
			
			if(selectorKey != null)
			{
				selectorKey.cancel();
			}
		}
	}
	
	@Override
	public void onConnect(Instant instant, DataInterface dataInterface)
	{
		if(this.shuttingDown || this.stopping)
		{
			return;
		}
		
		if(dataInterface instanceof SocketChannelDriver)
		{
			try
			{
				this.registerSelector((SocketChannelDriver)dataInterface);
			}
			catch(CommunicationException e)
			{
				LOGGER.error(e);
			}
		}
	}
	
	/**
	 * Registers the given {@link SocketChannelDriver}'s socket channel with the selector for read operations.
	 * This method ensures that the socket channel is ready for non-blocking read operations.
	 * 
	 * @param socketChannelDriver The {@link SocketChannelDriver} whose socket channel is to be registered.
	 * @throws CommunicationException if an I/O error occurs during the registration process.
	 */
	private void registerSelector(SocketChannelDriver socketChannelDriver) throws CommunicationException
	{
		if(this.shuttingDown || this.stopping)
		{
			return;
		}
		
		SocketChannel socketChannel = socketChannelDriver.getSocketChannel();
		
		try
		{
			if(socketChannel.isBlocking())
			{
				socketChannel.configureBlocking(false);
			}
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.CONFIGURE_NON_BLOCKING_ERROR, socketChannelDriver.getDynamicStringIdentifier());
			throw new CommunicationException(errorMessage, e);
		}
		
		Selector selector = this.getSelector();
		
		try
		{
			socketChannel.register(selector, SelectionKey.OP_READ);
		}
		catch(IOException e)
		{
			String errorMessage = MessageUtil.getMessage(Messages.OPENING_SELECTOR_ERROR, socketChannelDriver.getDynamicStringIdentifier());
			
			throw new CommunicationException(errorMessage, e);
		}
	}
	
	@Override
	public void onConnectError(Instant instant, DataInterface dataInterface, Throwable throwable)
	{
		String errorMessage;
		
		if(dataInterface == null)
		{
			errorMessage = MessageUtil.getMessage(Messages.CONNECTION_ERROR, (Object)null);
		}
		else
		{
			ConfigurationValues configurationValues = dataInterface.getConfigurationValues();
			
			errorMessage = MessageUtil.getMessage(Messages.CONNECTION_ERROR, configurationValues.toString());
		}
		
		LOGGER.debug(errorMessage, throwable);
	}
	
	@Override
	public void onDisconnectError(Instant instant, DataInterface dataInterface, Throwable throwable)
	{
		String errorMessage;
		
		if(dataInterface == null)
		{
			errorMessage = MessageUtil.getMessage(Messages.DISCONNECTION_ERROR, (Object)null);
		}
		else
		{
			errorMessage = MessageUtil.getMessage(Messages.DISCONNECTION_ERROR, this.getReceiverString(dataInterface));
		}
		
		LOGGER.debug(errorMessage, throwable);
	}
	
	/**
	 * Generates a string representation of the data receiver for logging purposes. This representation includes 
	 * a timestamp and the identifier of the data receiver.
	 *
	 * @param dataInterface the data interface whose string representation is to be generated
	 * @return a string representation of the data interface
	 */
	private String getReceiverString(DataInterface dataInterface)
	{
		StringBuilder sb = new StringBuilder();
		
		sb.append(Instant.now()).append(": ");
		sb.append(dataInterface.getDynamicStringIdentifier());
		
		return sb.toString();
	}
}
