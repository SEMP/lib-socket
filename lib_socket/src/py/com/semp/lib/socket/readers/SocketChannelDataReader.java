package py.com.semp.lib.socket.readers;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import py.com.semp.lib.socket.SocketChannelDriver;
import py.com.semp.lib.socket.internal.MessageUtil;
import py.com.semp.lib.socket.internal.Messages;
import py.com.semp.lib.utilidades.communication.interfaces.DataInterface;
import py.com.semp.lib.utilidades.communication.interfaces.DataReader;
import py.com.semp.lib.utilidades.communication.listeners.ConnectionEventListener;
import py.com.semp.lib.utilidades.configuration.Values;
import py.com.semp.lib.utilidades.exceptions.CommunicationException;
import py.com.semp.lib.utilidades.exceptions.CommunicationTimeoutException;
import py.com.semp.lib.utilidades.log.Logger;
import py.com.semp.lib.utilidades.log.LoggerManager;

//TODO modificar para permitir manejar varios SocketChannelDriver.

public class SocketChannelDataReader implements DataReader, ConnectionEventListener
{
	private SocketChannelDriver dataReceiver;
	private Selector selector;
	
	private volatile boolean pauseReading = false;
	private volatile boolean isReading = false;
	private volatile boolean readingComplete = false;
	private volatile boolean stopping = false;
	private volatile AtomicBoolean threadNameUpdated  = new AtomicBoolean(false);
	
	private static final Logger LOGGER = LoggerManager.getLogger(Values.Constants.UTILITIES_CONTEXT);
	
	public SocketChannelDataReader(SocketChannelDriver socketChannelDriver)
	{
		super();
		
		this.dataReceiver = socketChannelDriver;
		this.dataReceiver.addConnectionEventListeners(this);
	}
	
	public Selector getSelector() throws CommunicationException
	{
		if(this.selector == null)
		{
			try
			{
				this.selector = Selector.open();
				SocketChannel socketChannel = this.dataReceiver.getSocketChannel();
				socketChannel.register(this.selector, SelectionKey.OP_READ);
			}
			catch(IOException e)
			{
				throw new CommunicationException(e);
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
		Integer readTimeoutMS = this.getConfiguration(Values.VariableNames.READ_TIMEOUT_MS, Values.Defaults.READ_TIMEOUT_MS);
		
		if(readTimeoutMS < 0)
		{
			readTimeoutMS = 0;
		}
		
		this.setThreadName();
		
		while(true)
		{
			if(this.dataReceiver.isShuttingdown())
			{
				//TODO ver como manejar el shutdown (siendo que puede haber varias conexiones)
				this.shutdown();
				
				return;
			}
			
			if(this.stopping || this.dataReceiver.isStopping())
			{
				break;
			}
			
			if(!this.pauseReading && this.dataReceiver.isConnected())
			{
				if(this.threadNameUpdated.compareAndSet(false, true))
				{
					this.setThreadName();
				}
				
				try
				{
					this.isReading = true;
					
					this.readWithTimeout(readTimeoutMS);
				}
				catch(CommunicationException e)
				{
					this.stopReading();
				}
			}
			else
			{
				this.isReading = false;
			}
		}
		
		this.completeReading();
	}
	
	private void setThreadName()
	{
		Thread currentThread = Thread.currentThread();
		
		StringBuilder threadName = new StringBuilder();
		
		threadName.append(this.getClass().getSimpleName());
		threadName.append("_");
		threadName.append(currentThread.getId());
		threadName.append("_");
		threadName.append(this.dataReceiver.getDynamicStringIdentifier());
		
		currentThread.setName(threadName.toString());
	}
	
	/**
	 * Attempts to read data with a specified timeout. If data is not received within the timeout period, 
	 * a CommunicationTimeoutException is thrown. This method repeatedly tries to read data until it is successful 
	 * or the timeout is exceeded.
	 *
	 * @param readTimeoutMS the maximum time in miliseconds to wait for data to be read
	 * @return the read data as a byte array
	 * @throws CommunicationException if there is an issue with reading the data or a timeout occurs
	 */
	private byte[] readWithTimeout(int readTimeoutMS) throws CommunicationException
	{
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
	
	@Override
	public void startReading()
	{
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void pauseReading()
	{
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void stopReading()
	{
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public boolean isReading()
	{
		// TODO Auto-generated method stub
		return false;
	}
	
	@Override
	public boolean isReadingComplete()
	{
		// TODO Auto-generated method stub
		return false;
	}
	
	@Override
	public void shutdown()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onDisconnect(Instant instant, DataInterface dataInterface)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onConnect(Instant instant, DataInterface dataInterface)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onConnectError(Instant instant, DataInterface dataInterface, Throwable throwable)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onDisconnectError(Instant instant, DataInterface dataInterface, Throwable throwable)
	{
		// TODO Auto-generated method stub
		
	}
}
