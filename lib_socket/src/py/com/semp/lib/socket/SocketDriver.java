package py.com.semp.lib.socket;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import py.com.semp.lib.log.utilities.LoggerFactory;
import py.com.semp.lib.socket.configuration.SocketConfiguration;
import py.com.semp.lib.socket.configuration.Values;
import py.com.semp.lib.socket.internal.MessageUtil;
import py.com.semp.lib.socket.internal.Messages;
import py.com.semp.lib.utilidades.communication.DataInterface;
import py.com.semp.lib.utilidades.communication.DataReceiver;
import py.com.semp.lib.utilidades.communication.DataTransmitter;
import py.com.semp.lib.utilidades.communication.listeners.ConnectionEventListener;
import py.com.semp.lib.utilidades.communication.listeners.DataListener;
import py.com.semp.lib.utilidades.configuration.ConfigurationValues;
import py.com.semp.lib.utilidades.exceptions.CommunicationException;
import py.com.semp.lib.utilidades.log.Logger;

public class SocketDriver implements DataInterface, DataReceiver, DataTransmitter
{
	private static final Logger LOGGER = LoggerFactory.getLogger(Values.Constants.SOCKET_CONTEXT);
	
	private final ReentrantLock socketLock = new ReentrantLock();
	private Socket socket;
	private SocketConfiguration configurationValues;
	private final CopyOnWriteArraySet<DataListener> dataListeners = new CopyOnWriteArraySet<>();
	private final CopyOnWriteArraySet<ConnectionEventListener> connectionEventListeners = new CopyOnWriteArraySet<>();
	private volatile AtomicBoolean connected = new AtomicBoolean(false);
	private volatile String stringIdentifier;
	private final ExecutorService executorService = Executors.newFixedThreadPool(Values.Constants.SOCKET_LISTENERS_THREAD_POOL_SIZE);
	
	public SocketDriver() throws CommunicationException
	{
		this(new Socket());
	}
	
	public SocketDriver(Socket socket) throws CommunicationException
	{
		super();
		
		this.setSocket(socket);
	}
	
	private void setSocket(Socket socket) throws CommunicationException
	{
		this.connected.set(false);
		
		if(socket == null)
		{
			StringBuilder methodName = new StringBuilder();
			
			methodName.append("void ");
			methodName.append(this.getClass().getSimpleName());
			methodName.append("::");
			methodName.append("setSocket(Socket)");
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName.toString());
			
			throw new NullPointerException(errorMessage);
		}
		
		this.socketLock.lock();
		
		try
		{
			this.socket = socket;
			
			if(this.socket.isConnected() && !this.socket.isClosed())
			{
				this.connected.set(true);
				
				this.setConfiguredSoTimeout();
			}
		}
		finally
		{
			this.socketLock.unlock();
		}
	}
	
	private void setConfiguredSoTimeout() throws CommunicationException
	{
		Integer readTimeoutMS = this.configurationValues.getValue(Values.VariableNames.READ_TIMEOUT_MS);
		
		if(readTimeoutMS == null)
		{
			StringBuilder methodName = new StringBuilder();
			
			methodName.append("void ");
			methodName.append(this.getClass().getSimpleName());
			methodName.append("::");
			methodName.append("setConfiguredSoTimeout()");
			
			String errorMessage = MessageUtil.getMessage(Messages.VALUE_SHOULD_NOT_BE_NULL_ERROR, Values.VariableNames.READ_TIMEOUT_MS, methodName.toString());
			
			throw new CommunicationException(errorMessage);
		}
		
		this.setSoTimeout(readTimeoutMS);
	}
	
	private void setSoTimeout(int readTimeoutMS) throws CommunicationException
	{
		try
		{
			this.socket.setSoTimeout(readTimeoutMS);
		}
		catch(SocketException e)
		{
			StringBuilder methodName = new StringBuilder();
			
			methodName.append(this.getClass().getSimpleName());
			methodName.append(" ");
			methodName.append(this.getStringIdentifier());
			
			String errorMessage = MessageUtil.getMessage(Messages.UNABLE_TO_SET_CONFIGURATION_ERROR, "soTimeout", Integer.toString(readTimeoutMS), methodName.toString());
			
			throw new CommunicationException(errorMessage, e);
		}
	}
	
	public Socket getSocket()
	{
		return this.socket;
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
	public SocketDriver addDataListeners(DataListener... listeners)
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
	public SocketDriver removeDataListeners(DataListener... listeners)
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
	public SocketDriver removeAllDataListeners()
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
	
	@Override
	public DataInterface disconnect() throws CommunicationException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public SocketDriver setConfigurationValues(ConfigurationValues configurationValues) throws CommunicationException
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
	public SocketDriver addConnectionEventListeners(ConnectionEventListener... listeners)
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
	public SocketDriver removeConnectionEventListeners(ConnectionEventListener... listeners)
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
	public SocketDriver removeAllConnectionEventListeners()
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
		Socket socket = this.getSocket();
		
		if(socket == null)
		{
			return false;
		}
		
		boolean socketConnected = socket.isConnected() && !socket.isClosed();
		
		return this.connected.get() && socketConnected;
	}
}