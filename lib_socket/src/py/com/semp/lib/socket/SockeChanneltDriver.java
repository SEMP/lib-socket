package py.com.semp.lib.socket;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import py.com.semp.lib.socket.configuration.SocketConfiguration;
import py.com.semp.lib.socket.configuration.Values;
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

public class SockeChanneltDriver implements DataInterface, DataReceiver, DataTransmitter
{
	private static final Logger LOGGER = LoggerManager.getLogger(Values.Constants.SOCKET_CONTEXT);
	
//	private Socket socket;
	private SocketChannel socketChannel;
	private volatile String stringIdentifier;
	private SocketConfiguration configurationValues;
//	private volatile AtomicBoolean connected = new AtomicBoolean(false);
	private final Thread shutdownHook = new Thread(new ShutdownHookAction(this));
	private final CopyOnWriteArraySet<DataListener> dataListeners = new CopyOnWriteArraySet<>();
	private final CopyOnWriteArraySet<ConnectionEventListener> connectionEventListeners = new CopyOnWriteArraySet<>();
	private final ExecutorService executorService = Executors.newFixedThreadPool(Values.Constants.SOCKET_LISTENERS_THREAD_POOL_SIZE);
	private final ReentrantLock socketLock = new ReentrantLock();
	
	public SockeChanneltDriver() throws CommunicationException
	{
		this(getNewSocketChannel());
	}

	public SockeChanneltDriver(SocketChannel socketChannel) throws CommunicationException
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
			StringBuilder methodName = new StringBuilder();
			
			methodName.append("void ");
			methodName.append(this.getClass().getSimpleName());
			methodName.append("::");
			methodName.append("setSocketChannel(SocketChannel)");
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName.toString());
			
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
		if(!this.isConnected())
		{
			String errorMessage = MessageUtil.getMessage(Messages.SOCKET_CLOSED_OR_NOT_CONNECTED_ERROR, this.getStringIdentifier());
			
			throw new CommunicationException(errorMessage);
		}
		
		this.socketLock.lock();
		
		try
		{
			if(!this.isConnected())
			{
				String errorMessage = MessageUtil.getMessage(Messages.SOCKET_CLOSED_OR_NOT_CONNECTED_ERROR, this.getStringIdentifier());
				
				throw new CommunicationException(errorMessage);
			}
			
			OutputStream outputStream = this.socket.getOutputStream();
			
			outputStream.write(data);
			outputStream.flush();
			
			this.informSent(data);
		}
		catch(IOException e)
		{
			this.disconnect();
			
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
		this.executorService.submit(() ->
		{
			for(DataListener listener : this.dataListeners)
			{
				try
				{
					listener.onDataSent(Instant.now(), this, data);
				}
				catch(Exception e)
				{
					String errorMessage = MessageUtil.getMessage(Messages.LISTENER_THROWN_EXCEPTION_ERROR, listener.getClass().getName());
					
					LOGGER.warning(errorMessage, e);
				}
			}
		});
	}
	
	@Override
	public SockeChanneltDriver addDataListeners(DataListener... listeners)
	{
		if(listeners == null)
		{
			StringBuilder methodName = new StringBuilder();
			
			methodName.append("SocketDriver ");
			methodName.append(this.getClass().getSimpleName());
			methodName.append("::");
			methodName.append("addDataListeners(DataListener...)");
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName.toString());
			
			throw new NullPointerException(errorMessage);
		}
		
		this.dataListeners.addAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SockeChanneltDriver removeDataListeners(DataListener... listeners)
	{
		if(listeners == null)
		{
			StringBuilder methodName = new StringBuilder();
			
			methodName.append("SocketDriver ");
			methodName.append(this.getClass().getSimpleName());
			methodName.append("::");
			methodName.append("removeDataListeners(DataListener...)");
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName.toString());
			
			throw new NullPointerException(errorMessage);
		}
		
		this.dataListeners.removeAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SockeChanneltDriver removeAllDataListeners()
	{
		this.dataListeners.clear();
		
		return this;
	}
	
	@Override
	public byte[] readData() throws CommunicationException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public DataInterface connect() throws CommunicationException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public DataInterface connect(ConfigurationValues configurationValues) throws CommunicationException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
//	@Override
//	public SocketDriver disconnect() throws CommunicationException
//	{
//		this.socketLock.lock();
//		
//		try
//		{
//			this.connected.set(false);
//			
//			this.informDisconnected();
//			
//			this.executorService.shutdown();
//			
//			if(this.socket != null)
//			{
//				if(!this.socket.isClosed())
//				{
//					this.socket.shutdownOutput();
//				}
//			}
//		}
//		catch(IOException e)
//		{
//			String errorMessage = MessageUtil.getMessage(Messages.DISCONNECTING_ERROR, this.getStringIdentifier());
//			
//			throw new CommunicationException(errorMessage, e);
//		}
//		finally
//		{
//			try
//			{
//				this.socket.close();
//			}
//			catch(IOException e)
//			{
//				String errorMessage = MessageUtil.getMessage(Messages.DISCONNECTING_ERROR, this.getStringIdentifier());
//				
//				throw new CommunicationException(errorMessage, e);
//			}
//			finally
//			{
//				this.socketLock.unlock();
//			}
//		}
//		
//		return this;
//	}
	
	@Override
	public SockeChanneltDriver disconnect() throws CommunicationException
	{
		this.socketLock.lock();
		
		IOException suppressedException = null;
		
		try
		{
			this.connected.set(false);
			this.informDisconnected();
			this.executorService.shutdown();
			
			if(this.socket != null && !this.socket.isClosed())
			{
				this.socket.shutdownOutput();
			}
		}
		catch(IOException e)
		{
			suppressedException = e;
		}
		finally
		{
			try
			{
				if(this.socket != null)
				{
					this.socket.close();
					this.removeShutdownHook();
				}
			}
			catch(IOException e)
			{
				String errorMessage = MessageUtil.getMessage(Messages.DISCONNECTING_ERROR, this.getStringIdentifier());
				
				if(suppressedException != null)
				{
					suppressedException.addSuppressed(e);
					
					throw new CommunicationException(errorMessage, suppressedException);
				}
				else
				{
					throw new CommunicationException(errorMessage, e);
				}
			}
			finally
			{
				this.socketLock.unlock();
			}
			
			if(suppressedException != null)
			{
				String errorMessage = MessageUtil.getMessage(Messages.DISCONNECTING_ERROR, this.getStringIdentifier());
				
				throw new CommunicationException(errorMessage, suppressedException);
			}
		}
		
		return this;
	}
	
	public SockeChanneltDriver shutdown() throws CommunicationException
	{
		this.socketLock.lock();
		
		try
		{
			this.executorService.shutdownNow();
			
			if(this.socket != null && !this.socket.isClosed())
			{
				this.socket.close();
				this.removeShutdownHook();
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
		this.executorService.submit(() ->
		{
			for(ConnectionEventListener listener : this.connectionEventListeners)
			{
				try
				{
					listener.onDisconnect(Instant.now(), this);
				}
				catch(Exception e)
				{
					String errorMessage = MessageUtil.getMessage(Messages.LISTENER_THROWN_EXCEPTION_ERROR, listener.getClass().getName());
					
					LOGGER.warning(errorMessage, e);
				}
			}
		});
	}
	
	@Override
	public SockeChanneltDriver setConfigurationValues(ConfigurationValues configurationValues) throws CommunicationException
	{
		this.checkConfigurationValues(configurationValues);
		
		this.configurationValues = (SocketConfiguration) configurationValues;
		
		return this;
	}
	
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
	
	@Override
	public SockeChanneltDriver addConnectionEventListeners(ConnectionEventListener... listeners)
	{
		if(listeners == null)
		{
			StringBuilder methodName = new StringBuilder();
			
			methodName.append("SocketDriver ");
			methodName.append(this.getClass().getSimpleName());
			methodName.append("::");
			methodName.append("addConnectionEventListeners(ConnectionEventListener...)");
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName.toString());
			
			throw new NullPointerException(errorMessage);
		}
		
		this.connectionEventListeners.addAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SockeChanneltDriver removeConnectionEventListeners(ConnectionEventListener... listeners)
	{
		if(listeners == null)
		{
			StringBuilder methodName = new StringBuilder();
			
			methodName.append("SocketDriver ");
			methodName.append(this.getClass().getSimpleName());
			methodName.append("::");
			methodName.append("removeConnectionEventListeners(ConnectionEventListener...)");
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName.toString());
			
			throw new NullPointerException(errorMessage);
		}
		
		this.connectionEventListeners.removeAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SockeChanneltDriver removeAllConnectionEventListeners()
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
		return this.socket.getRemoteSocketAddress();
	}
	
	public SocketAddress getLocalAddress()
	{
		return this.socket.getLocalSocketAddress();
	}
	
	public static String getAddressString(SocketAddress socketAddress)
	{
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
		
		if(socketAddress == null)
		{
			return null;
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