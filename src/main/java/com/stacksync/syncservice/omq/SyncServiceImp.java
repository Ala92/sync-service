package com.stacksync.syncservice.omq;

import java.util.List;

import omq.common.broker.Broker;
import omq.common.util.ParameterQueue;
import omq.exception.RemoteException;
import omq.server.RemoteObject;

import org.apache.log4j.Logger;

import com.stacksync.commons.models.CommitInfo;
import com.stacksync.commons.models.Device;
import com.stacksync.commons.models.ItemMetadata;
import com.stacksync.commons.models.User;
import com.stacksync.commons.models.Workspace;
import com.stacksync.commons.notifications.CommitNotification;
import com.stacksync.commons.notifications.ShareProposalNotification;
import com.stacksync.commons.omq.ISyncService;
import com.stacksync.commons.omq.RemoteClient;
import com.stacksync.commons.omq.RemoteWorkspace;
import com.stacksync.commons.requests.GetWorkspacesRequest;
import com.stacksync.commons.requests.ShareProposalRequest;
import com.stacksync.commons.requests.UpdateDeviceRequest;
import com.stacksync.syncservice.db.ConnectionPool;
import com.stacksync.commons.exceptions.DeviceNotUpdatedException;
import com.stacksync.commons.exceptions.DeviceNotValidException;
import com.stacksync.commons.exceptions.NoWorkspacesFoundException;
import com.stacksync.commons.exceptions.ShareProposalNotCreatedException;
import com.stacksync.commons.exceptions.UserNotFoundException;
import com.stacksync.syncservice.exceptions.dao.DAOException;
import com.stacksync.syncservice.handler.Handler;
import com.stacksync.syncservice.handler.SQLHandler;

public class SyncServiceImp extends RemoteObject implements ISyncService {

	private transient static final Logger logger = Logger.getLogger(SyncServiceImp.class.getName());
	private transient static final long serialVersionUID = 1L;
	private transient int index;
	private transient int numThreads;
	private transient ConnectionPool pool;
	private transient Handler[] handlers;
	private transient Broker broker;

	public SyncServiceImp(Broker broker, ConnectionPool pool) throws Exception {
		super();
		this.broker = broker;
		this.pool = pool;

		index = 0;
		// Create handlers
		numThreads = Integer.parseInt(this.broker.getEnvironment().getProperty(ParameterQueue.NUM_THREADS, "1"));
		handlers = new Handler[numThreads];

		for (int i = 0; i < numThreads; i++) {
			handlers[i] = new SQLHandler(this.pool);
		}

	}

	@Override
	public List<ItemMetadata> getChanges(String requestId, User user, Workspace workspace) {
		logger.debug("GetChanges -->[User:" + user + ", Request:" + requestId + ", Workspace: " + workspace + "]");

		List<ItemMetadata> list = getHandler().doGetChanges(user, workspace);
		return list;
	}

	@Override
	public List<Workspace> getWorkspaces(GetWorkspacesRequest request) throws NoWorkspacesFoundException {
		logger.debug(request.toString());

		User user = new User();
		user.setCloudId(request.getUserId());

		List<Workspace> workspaces = getHandler().doGetWorkspaces(user);

		return workspaces;
	}

	@Override
	public void commit(String requestId, User user, Workspace workspace, Device device, List<ItemMetadata> items) {
		logger.debug("Commit -->[User:" + user + ", Request:" + requestId + ", RemoteWorkspace:" + workspace
				+ ", Device: " + device + "]");
		logger.debug("Commit objects -> " + items);

		try {
			List<CommitInfo> committedItems = getHandler().doCommit(user, workspace, device, items);

			CommitNotification result = new CommitNotification(requestId, committedItems);
			String id = Long.toString(workspace.getId());

			RemoteWorkspace commitNotifier = broker.lookupMulti(id, RemoteWorkspace.class);
			commitNotifier.notifyCommit(result);

			logger.debug("Consumer: Response sent to workspace \"" + workspace + "\"");
		} catch (DAOException e) {
			// debe enterarse el cliente?
			// se recupera?
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private synchronized Handler getHandler() {
		Handler handler = handlers[index++ % numThreads];
		logger.debug("Using handler: " + handler + " using connection: " + handler.getConnection());
		return handler;
	}

	@Override
	public Long updateDevice(UpdateDeviceRequest request) throws UserNotFoundException, DeviceNotValidException,
			DeviceNotUpdatedException {

		logger.debug(request.toString());

		User user = new User();
		user.setCloudId(request.getUserId());

		Device device = new Device();
		device.setId(request.getDeviceId());
		device.setUser(user);
		device.setName(request.getDeviceName());
		device.setOs(request.getOs());
		device.setLastIp(request.getIp());
		device.setAppVersion(request.getAppVersion());

		Long deviceId = getHandler().doUpdateDevice(device);

		return deviceId;
	}

	@Override
	public Long createShareProposal(ShareProposalRequest request) throws ShareProposalNotCreatedException,
			UserNotFoundException {

		logger.debug(request);

		User user = new User();
		user.setCloudId(request.getUserId());

		//Create share proposal
		Workspace workspace = getHandler().doCreateShareProposal(user, request.getEmails(), request.getFolderName());

		// Create notification
		ShareProposalNotification notification = new ShareProposalNotification(workspace.getId(),
				request.getFolderName(), 0L, workspace.getOwner().getCloudId(), workspace.getOwner().getName());

		//Send notifications to users
		for (User addressee : workspace.getUsers()) {
			try {
				RemoteClient client = broker.lookupMulti(addressee.getCloudId(), RemoteClient.class);
				client.notifyShareProposal(notification);
			} catch (RemoteException e) {
				logger.error(String.format("Could not notify user (cloudId): '%s'", addressee.getCloudId()), e);
			}
		}

		return workspace.getId();
	}
}
