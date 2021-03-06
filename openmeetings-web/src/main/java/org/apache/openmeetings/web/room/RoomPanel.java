/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openmeetings.web.room;

import static java.time.Duration.ZERO;
import static java.util.Comparator.naturalOrder;
import static org.apache.openmeetings.core.util.ChatWebSocketHelper.ID_USER_PREFIX;
import static org.apache.openmeetings.web.app.WebSession.getDateFormat;
import static org.apache.openmeetings.web.app.WebSession.getUserId;
import static org.apache.openmeetings.web.room.wb.InterviewWbPanel.INTERVIEWWB_JS_REFERENCE;
import static org.apache.openmeetings.web.room.wb.WbPanel.WB_JS_REFERENCE;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.openmeetings.core.remote.KurentoHandler;
import org.apache.openmeetings.core.remote.StreamProcessor;
import org.apache.openmeetings.core.util.WebSocketHelper;
import org.apache.openmeetings.db.dao.calendar.AppointmentDao;
import org.apache.openmeetings.db.dao.user.UserDao;
import org.apache.openmeetings.db.entity.basic.Client;
import org.apache.openmeetings.db.entity.basic.Client.StreamDesc;
import org.apache.openmeetings.db.entity.calendar.Appointment;
import org.apache.openmeetings.db.entity.calendar.MeetingMember;
import org.apache.openmeetings.db.entity.file.BaseFileItem;
import org.apache.openmeetings.db.entity.room.Room;
import org.apache.openmeetings.db.entity.room.Room.Right;
import org.apache.openmeetings.db.entity.room.Room.RoomElement;
import org.apache.openmeetings.db.entity.room.RoomGroup;
import org.apache.openmeetings.db.entity.server.SOAPLogin;
import org.apache.openmeetings.db.entity.user.GroupUser;
import org.apache.openmeetings.db.entity.user.User;
import org.apache.openmeetings.db.util.AuthLevelUtil;
import org.apache.openmeetings.db.util.ws.RoomMessage;
import org.apache.openmeetings.db.util.ws.RoomMessage.Type;
import org.apache.openmeetings.db.util.ws.TextRoomMessage;
import org.apache.openmeetings.util.NullStringer;
import org.apache.openmeetings.web.app.ClientManager;
import org.apache.openmeetings.web.app.QuickPollManager;
import org.apache.openmeetings.web.app.TimerService;
import org.apache.openmeetings.web.app.WebSession;
import org.apache.openmeetings.web.common.BasePanel;
import org.apache.openmeetings.web.room.activities.Activity;
import org.apache.openmeetings.web.room.menu.RoomMenuPanel;
import org.apache.openmeetings.web.room.sidebar.RoomSidebar;
import org.apache.openmeetings.web.room.wb.AbstractWbPanel;
import org.apache.openmeetings.web.room.wb.InterviewWbPanel;
import org.apache.openmeetings.web.room.wb.WbAction;
import org.apache.openmeetings.web.room.wb.WbPanel;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.authroles.authorization.strategies.role.annotations.AuthorizeInstantiation;
import org.apache.wicket.core.request.handler.IPartialPageRequestHandler;
import org.apache.wicket.event.IEvent;
import org.apache.wicket.extensions.ajax.AjaxDownloadBehavior;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.head.PriorityHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.protocol.ws.api.BaseWebSocketBehavior;
import org.apache.wicket.protocol.ws.api.event.WebSocketPushPayload;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.JavaScriptResourceReference;
import org.apache.wicket.request.resource.ResourceStreamResource;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.resource.AbstractResourceStream;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.ResourceStreamNotFoundException;
import org.apache.wicket.util.string.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;
import com.googlecode.wicket.jquery.core.JQueryBehavior;
import com.googlecode.wicket.jquery.core.Options;
import com.googlecode.wicket.jquery.ui.interaction.droppable.Droppable;

import de.agilecoders.wicket.core.markup.html.bootstrap.button.BootstrapAjaxLink;
import de.agilecoders.wicket.core.markup.html.bootstrap.button.Buttons;
import de.agilecoders.wicket.core.markup.html.bootstrap.dialog.Alert;
import de.agilecoders.wicket.core.markup.html.bootstrap.dialog.Modal;
import de.agilecoders.wicket.core.markup.html.bootstrap.dialog.Modal.Backdrop;
import de.agilecoders.wicket.core.markup.html.bootstrap.dialog.TextContentModal;

@AuthorizeInstantiation("ROOM")
public class RoomPanel extends BasePanel {
	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(RoomPanel.class);
	public static final String PARAM_ACTION = "action";
	private static final String ACCESS_DENIED_ID = "access-denied";
	private static final String EVENT_DETAILS_ID = "event-details";
	public enum Action {
		kick
		, muteOthers
		, mute
		, toggleRight
	}
	private final Room r;
	private final boolean interview;
	private final WebMarkupContainer room = new WebMarkupContainer("roomContainer");
	private final AbstractDefaultAjaxBehavior roomEnter = new AbstractDefaultAjaxBehavior() {
		private static final long serialVersionUID = 1L;

		@Override
		protected void respond(AjaxRequestTarget target) {
			log.debug("RoomPanel::roomEnter");
			WebSession ws = WebSession.get();
			Client c = getClient();
			JSONObject options = VideoSettings.getInitJson(c.getSid())
					.put("uid", c.getUid())
					.put("userId", c.getUserId())
					.put("rights", c.toJson(true).getJSONArray("rights"))
					.put("interview", interview)
					.put("audioOnly", r.isAudioOnly())
					.put("questions", r.isAllowUserQuestions())
					.put("showMicStatus", !r.getHiddenElements().contains(RoomElement.MICROPHONE_STATUS));
			if (!Strings.isEmpty(r.getRedirectURL()) && (ws.getSoapLogin() != null || ws.getInvitation() != null)) {
				options.put("reloadUrl", r.getRedirectURL());
			}
			StringBuilder sb = new StringBuilder("Room.init(").append(options.toString(new NullStringer())).append(");")
					.append(wb.getInitScript())
					.append(getQuickPollJs());
			if (c.hasRight(Room.Right.MODERATOR) || !r.isHidden(RoomElement.USER_COUNT)) {
				List<Client> list = cm.listByRoom(r.getId());
				list.sort(Comparator.<Client, Integer>comparing(cl -> {
					if (cl.hasRight(Room.Right.MODERATOR)) {
						return 0;
					}
					if (cl.hasRight(Room.Right.PRESENTER)) {
						return 1;
					}
					return 5;
				}, naturalOrder())
						.thenComparing(cl -> cl.getUser().getDisplayName(), String::compareToIgnoreCase));
				sb.append("Room.addClient([");
				list.stream().forEach(cl -> sb.append(cl.toJson(false).toString(new NullStringer())).append(","));
				sb.deleteCharAt(sb.length() - 1).append("]);");

			}
			target.appendJavaScript(sb);

			WebSocketHelper.sendRoom(new TextRoomMessage(r.getId(), c, RoomMessage.Type.ROOM_ENTER, c.getUid()));
			// play video from other participants
			initVideos(target);
			getMainPanel().getChat().roomEnter(r, target);
			if (r.isFilesOpened()) {
				sidebar.setFilesActive(target);
			}
			if (Room.Type.PRESENTATION != r.getType()) {
				List<Client> mods = cm.listByRoom(r.getId(), cl -> cl.hasRight(Room.Right.MODERATOR));
				log.debug("RoomPanel::roomEnter, mods IS EMPTY ? {}, is MOD ? {}", mods.isEmpty(), c.hasRight(Room.Right.MODERATOR));
				if (mods.isEmpty()) {
					showIdeaAlert(target, getString(r.isModerated() ? "641" : "498"));
				}
			}
			if (r.isWaitRecording()) {
				showIdeaAlert(target, getString("1315"));
			}
			wb.update(target);
		}

		private void initVideos(AjaxRequestTarget target) {
			StringBuilder sb = new StringBuilder();
			JSONArray streams = new JSONArray();
			for (Client c : cm.listByRoom(getRoom().getId())) {
				for (StreamDesc sd : c.getStreams()) {
					streams.put(sd.toJson());
				}
			}
			if (streams.length() > 0) {
				sb.append("VideoManager.play(").append(streams).append(", ").append(kHandler.getTurnServers()).append(");");
			}
			if (interview && streamProcessor.recordingAllowed(getClient())) {
				sb.append("WbArea.setRecEnabled(true);");
			}
			if (!Strings.isEmpty(sb)) {
				target.appendJavaScript(sb);
			}
		}
	};
	private RedirectMessageDialog roomClosed;
	private Modal<String> clientKicked;
	private Alert waitModerator;

	private RoomMenuPanel menu;
	private RoomSidebar sidebar;
	private final AbstractWbPanel wb;
	private byte[] pdfWb;
	private final AjaxDownloadBehavior download = new AjaxDownloadBehavior(new ResourceStreamResource() {
		private static final long serialVersionUID = 1L;

		{
			setCacheDuration(ZERO);
			setFileName("whiteboard.pdf");
		}

		@Override
		protected IResourceStream getResourceStream(Attributes attributes) {
			return new AbstractResourceStream() {
				private static final long serialVersionUID = 1L;

				@Override
				public InputStream getInputStream() throws ResourceStreamNotFoundException {
					return new ByteArrayInputStream(pdfWb);
				}

				@Override
				public void close() throws IOException {
					//no-op
				}
			};
		}
	}) {
		private static final long serialVersionUID = 1L;

		@Override
		protected void onDownloadCompleted(AjaxRequestTarget target) {
			super.onDownloadCompleted(target);
			pdfWb = null;
		}
	};
	Component eventDetail = new WebMarkupContainer(EVENT_DETAILS_ID).setVisible(false);

	@SpringBean
	private ClientManager cm;
	@SpringBean
	private UserDao userDao;
	@SpringBean
	private AppointmentDao apptDao;
	@SpringBean
	private QuickPollManager qpollManager;
	@SpringBean
	private KurentoHandler kHandler;
	@SpringBean
	private StreamProcessor streamProcessor;
	@SpringBean
	private TimerService timerService;

	public RoomPanel(String id, Room r) {
		super(id);
		this.r = r;
		this.interview = Room.Type.INTERVIEW == r.getType();
		this.wb = interview ? new InterviewWbPanel("whiteboard", this) : new WbPanel("whiteboard", this);
	}

	public void startDownload(IPartialPageRequestHandler handler, byte[] bb) {
		pdfWb = bb;
		download.initiate(handler);
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();
		//let's refresh user in client
		cm.update(getClient().updateUser(userDao));
		Component accessDenied = new WebMarkupContainer(ACCESS_DENIED_ID).setVisible(false);

		room.setOutputMarkupPlaceholderTag(true);
		room.add(menu = new RoomMenuPanel("menu", this));
		room.add(AttributeModifier.append("data-room-id", r.getId()));
		if (interview) {
			room.add(new WebMarkupContainer("wb-area").add(wb));
		} else {
			Droppable<BaseFileItem> wbArea = new Droppable<>("wb-area") {
				private static final long serialVersionUID = 1L;

				@Override
				public void onConfigure(JQueryBehavior behavior) {
					super.onConfigure(behavior);
					behavior.setOption("hoverClass", Options.asString("droppable-hover"));
					behavior.setOption("accept", Options.asString(".recorditem, .fileitem, .readonlyitem"));
				}

				@Override
				public void onDrop(AjaxRequestTarget target, Component component) {
					Object o = component.getDefaultModelObject();
					if (wb.isVisible() && o instanceof BaseFileItem) {
						BaseFileItem f = (BaseFileItem)o;
						if (sidebar.getFilesPanel().isSelected(f)) {
							for (Entry<String, BaseFileItem> e : sidebar.getFilesPanel().getSelected().entrySet()) {
								wb.sendFileToWb(e.getValue(), false);
							}
						} else {
							wb.sendFileToWb(f, false);
						}
					}
				}
			};
			room.add(wbArea.add(wb));
		}
		room.add(roomEnter);
		room.add(sidebar = new RoomSidebar("sidebar", this));
		add(roomClosed = new RedirectMessageDialog("room-closed", "1098", r.isClosed(), r.getRedirectURL()));
		if (r.isClosed()) {
			room.setVisible(false);
		} else if (cm.listByRoom(r.getId()).size() >= r.getCapacity()) {
			accessDenied = new ExpiredMessageDialog(ACCESS_DENIED_ID, getString("99"), menu);
			room.setVisible(false);
		} else if (r.getId().equals(WebSession.get().getRoomId())) {
			// secureHash/invitationHash, already checked
		} else {
			boolean allowed = false;
			String deniedMessage = null;
			if (r.isAppointment()) {
				Appointment a = apptDao.getByRoom(r.getId());
				if (a != null && !a.isDeleted()) {
					boolean isOwner = a.getOwner().getId().equals(getUserId());
					allowed = isOwner;
					log.debug("appointed room, isOwner ? {}", isOwner);
					if (!allowed) {
						for (MeetingMember mm : a.getMeetingMembers()) {
							if (getUserId().equals(mm.getUser().getId())) {
								allowed = true;
								break;
							}
						}
					}
					if (allowed) {
						Calendar c = WebSession.getCalendar();
						if (isOwner || c.getTime().after(a.getStart()) && c.getTime().before(a.getEnd())) {
							eventDetail = new EventDetailDialog(EVENT_DETAILS_ID, a);
						} else {
							allowed = false;
							deniedMessage = String.format("%s %s - %s", getString("error.hash.period"), getDateFormat().format(a.getStart()), getDateFormat().format(a.getEnd()));
						}
					}
				}
			} else {
				allowed = r.getIspublic() || (r.getOwnerId() != null && r.getOwnerId().equals(getUserId()));
				log.debug("public ? {}, ownedId ? {} {}", r.getIspublic(), r.getOwnerId(), allowed);
				if (!allowed) {
					User u = getClient().getUser();
					for (RoomGroup ro : r.getGroups()) {
						for (GroupUser ou : u.getGroupUsers()) {
							if (ro.getGroup().getId().equals(ou.getGroup().getId())) {
								allowed = true;
								break;
							}
						}
						if (allowed) {
							break;
						}
					}
				}
			}
			if (!allowed) {
				if (deniedMessage == null) {
					deniedMessage = getString("1599");
				}
				accessDenied = new ExpiredMessageDialog(ACCESS_DENIED_ID, deniedMessage, menu);
				room.setVisible(false);
			}
		}
		RepeatingView groupstyles = new RepeatingView("groupstyle");
		add(groupstyles.setVisible(room.isVisible() && !r.getGroups().isEmpty()));
		if (room.isVisible()) {
			add(new NicknameDialog("nickname", this));
			add(download);
			add(new BaseWebSocketBehavior("media"));
			for (RoomGroup rg : r.getGroups()) {
				WebMarkupContainer groupstyle = new WebMarkupContainer(groupstyles.newChildId());
				groupstyle.add(AttributeModifier.append("href"
						, (String)RequestCycle.get().urlFor(new GroupCustomCssResourceReference(), new PageParameters().add("id", rg.getGroup().getId()))
						));
				groupstyles.add(groupstyle);
			}
			//We are setting initial rights here
			Client c = getClient();
			final int count = cm.addToRoom(c.setRoom(getRoom()));
			SOAPLogin soap = WebSession.get().getSoapLogin();
			if (soap != null && soap.isModerator()) {
				c.allow(Right.SUPER_MODERATOR);
				cm.update(c);
			} else {
				Set<Right> rr = AuthLevelUtil.getRoomRight(c.getUser(), r, r.isAppointment() ? apptDao.getByRoom(r.getId()) : null, count);
				if (!rr.isEmpty()) {
					c.allow(rr);
					cm.update(c);
					log.info("Setting rights for client:: {} -> {}", rr, c.hasRight(Right.MODERATOR));
				}
			}
			if (r.isModerated() && r.isWaitModerator()
					&& !c.hasRight(Right.MODERATOR)
					&& cm.listByRoom(r.getId(), cl -> cl.hasRight(Right.MODERATOR)).isEmpty())
			{
				room.setVisible(false);
				createWaitModerator(true);
				getMainPanel().getChat().toggle(null, false);
			}
			timerService.scheduleModCheck(r);
		} else {
			add(new WebMarkupContainer("nickname").setVisible(false));
		}
		if (waitModerator == null) {
			createWaitModerator(false);
		}
		add(room, accessDenied, eventDetail, waitModerator);
		add(clientKicked = new TextContentModal("client-kicked", new ResourceModel("606")));
		clientKicked
			.header(new ResourceModel("797"))
			.setCloseOnEscapeKey(false)
			.setBackdrop(Backdrop.FALSE)
			.addButton(new BootstrapAjaxLink<>("button", Model.of(""), Buttons.Type.Outline_Primary, new ResourceModel("54")) {
				private static final long serialVersionUID = 1L;

				public void onClick(AjaxRequestTarget target) {
					clientKicked.close(target);
					menu.exit(target);
				}
			});
	}

	@Override
	public void onEvent(IEvent<?> event) {
		Client _c = getClient();
		if (_c != null && event.getPayload() instanceof WebSocketPushPayload) {
			WebSocketPushPayload wsEvent = (WebSocketPushPayload) event.getPayload();
			if (wsEvent.getMessage() instanceof RoomMessage) {
				RoomMessage m = (RoomMessage)wsEvent.getMessage();
				IPartialPageRequestHandler handler = wsEvent.getHandler();
				switch (m.getType()) {
					case POLL_CREATED:
						menu.updatePoll(handler, m.getUserId());
						break;
					case POLL_UPDATED:
						menu.updatePoll(handler, null);
						break;
					case RECORDING_TOGGLED:
						menu.update(handler);
						updateInterviewRecordingButtons(handler);
						break;
					case SHARING_TOGGLED:
						menu.update(handler);
						break;
					case RIGHT_UPDATED:
						{
							String uid = ((TextRoomMessage)m).getText();
							Client c = cm.get(uid);
							if (c == null) {
								log.error("Not existing user in rightUpdated {} !!!!", uid);
								return;
							}
							boolean self = _c.getUid().equals(c.getUid());
							handler.appendJavaScript(String.format("Room.updateClient(%s);"
									, c.toJson(self).toString(new NullStringer())));
							sidebar.update(handler);
							menu.update(handler);
							wb.update(handler);
							updateInterviewRecordingButtons(handler);
						}
						break;
					case ROOM_ENTER:
						{
							sidebar.update(handler);
							menu.update(handler);
							String uid = ((TextRoomMessage)m).getText();
							Client c = cm.get(uid);
							if (c == null) {
								log.error("Not existing user in rightUpdated {} !!!!", uid);
								return;
							}
							if (c.hasRight(Room.Right.MODERATOR) || !r.isHidden(RoomElement.USER_COUNT)) {
								boolean self = _c.getUid().equals(c.getUid());
								handler.appendJavaScript(String.format("Room.addClient([%s]);"
										, c.toJson(self).toString(new NullStringer())));
							}
							sidebar.addActivity(new Activity(m, Activity.Type.roomEnter), handler);
						}
						break;
					case ROOM_EXIT:
						{
							String uid = ((TextRoomMessage)m).getText();
							sidebar.update(handler);
							sidebar.addActivity(new Activity(m, Activity.Type.roomExit), handler);
							handler.appendJavaScript("Room.removeClient('" + uid + "'); Chat.removeTab('" + ID_USER_PREFIX + m.getUserId() + "');");
						}
						break;
					case ROOM_CLOSED:
						handler.add(room.setVisible(false));
						roomClosed.show(handler);
						break;
					case REQUEST_RIGHT_MODERATOR:
						sidebar.addActivity(new Activity((TextRoomMessage)m, Activity.Type.reqRightModerator), handler);
						break;
					case REQUEST_RIGHT_PRESENTER:
						sidebar.addActivity(new Activity((TextRoomMessage)m, Activity.Type.reqRightPresenter), handler);
						break;
					case REQUEST_RIGHT_WB:
						sidebar.addActivity(new Activity((TextRoomMessage)m, Activity.Type.reqRightWb), handler);
						break;
					case REQUEST_RIGHT_SHARE:
						sidebar.addActivity(new Activity((TextRoomMessage)m, Activity.Type.reqRightShare), handler);
						break;
					case REQUEST_RIGHT_REMOTE:
						sidebar.addActivity(new Activity((TextRoomMessage)m, Activity.Type.reqRightRemote), handler);
						break;
					case REQUEST_RIGHT_A:
						sidebar.addActivity(new Activity((TextRoomMessage)m, Activity.Type.reqRightA), handler);
						break;
					case REQUEST_RIGHT_AV:
						sidebar.addActivity(new Activity((TextRoomMessage)m, Activity.Type.reqRightAv), handler);
						break;
					case REQUEST_RIGHT_MUTE_OTHERS:
						sidebar.addActivity(new Activity((TextRoomMessage)m, Activity.Type.reqRightMuteOthers), handler);
						break;
					case ACTIVITY_REMOVE:
						sidebar.removeActivity(((TextRoomMessage)m).getText(), handler);
						break;
					case HAVE_QUESTION:
						if (_c.hasRight(Room.Right.MODERATOR) || getUserId().equals(m.getUserId())) {
							sidebar.addActivity(new Activity((TextRoomMessage)m, Activity.Type.haveQuestion), handler);
						}
						break;
					case KICK:
						{
							String uid = ((TextRoomMessage)m).getText();
							if (_c.getUid().equals(uid)) {
								handler.add(room.setVisible(false));
								getMainPanel().getChat().toggle(handler, false);
								clientKicked.show(handler);
								cm.exitRoom(_c);
							}
						}
						break;
					case MUTE:
					{
						JSONObject obj = new JSONObject(((TextRoomMessage)m).getText());
						Client c = cm.getBySid(obj.getString("sid"));
						if (c == null) {
							log.error("Not existing user in mute {} !!!!", obj);
							return;
						}
						if (!_c.getUid().equals(c.getUid())) {
							handler.appendJavaScript(String.format("if (typeof(VideoManager) !== 'undefined') {VideoManager.mute('%s', %s);}", obj.getString("uid"), obj.getBoolean("mute")));
						}
					}
						break;
					case MUTE_OTHERS:
					{
						String uid = ((TextRoomMessage)m).getText();
						Client c = cm.get(uid);
						if (c == null) {
							// no luck
							return;
						}
						handler.appendJavaScript(String.format("if (typeof(VideoManager) !== 'undefined') {VideoManager.muteOthers('%s');}", uid));
					}
						break;
					case QUICK_POLL_UPDATED:
					{
						menu.update(handler);
						handler.appendJavaScript(getQuickPollJs());
					}
						break;
					case KURENTO_STATUS:
						menu.update(handler);
						break;
					case WB_RELOAD:
						if (Room.Type.INTERVIEW != r.getType()) {
							wb.reloadWb(handler);
						}
						break;
					case MODERATOR_IN_ROOM: {
						if (!r.isModerated() || !r.isWaitModerator()) {
							log.warn("Something weird: `moderatorInRoom` in wrong room {}", r);
						} else if (!_c.hasRight(Room.Right.MODERATOR)) {
							boolean moderInRoom = Boolean.TRUE.equals(Boolean.valueOf(((TextRoomMessage)m).getText()));
							log.warn("!! moderatorInRoom: {}", moderInRoom);
							if (room.isVisible() != moderInRoom) {
								handler.add(room.setVisible(moderInRoom));
								getMainPanel().getChat().toggle(handler, moderInRoom && !r.isHidden(RoomElement.CHAT));
								if (room.isVisible()) {
									handler.appendJavaScript(roomEnter.getCallbackScript());
									handler.add(waitModerator.setVisible(false));
								} else {
									handler.add(waitModerator.setVisible(true));
								}
							}
						}
					}
						break;
				}
			}
		}
		super.onEvent(event);
	}

	private String getQuickPollJs() {
		return String.format("Room.quickPoll(%s);", qpollManager.toJson(r.getId()));
	}

	private void updateInterviewRecordingButtons(IPartialPageRequestHandler handler) {
		Client _c = getClient();
		if (interview && _c.hasRight(Right.MODERATOR)) {
			if (streamProcessor.isRecording(r.getId())) {
				handler.appendJavaScript("if (typeof(WbArea) === 'object') {WbArea.setRecStarted(true);}");
			} else if (streamProcessor.recordingAllowed(getClient())) {
				boolean hasStreams = false;
				for (Client cl : cm.listByRoom(r.getId())) {
					if (!cl.getStreams().isEmpty()) {
						hasStreams = true;
						break;
					}
				}
				handler.appendJavaScript(String.format("if (typeof(WbArea) === 'object') {WbArea.setRecStarted(false);WbArea.setRecEnabled(%s);}", hasStreams));
			}
		}
	}

	public boolean isModerator(long userId, long roomId) {
		return isModerator(cm, userId, roomId);
	}

	public static boolean isModerator(ClientManager cm, long userId, long roomId) {
		return hasRight(cm, userId, roomId, Right.MODERATOR);
	}

	public static boolean hasRight(ClientManager cm, long userId, long roomId, Right r) {
		for (Client c : cm.listByRoom(roomId)) {
			if (c.sameUserId(userId) && c.hasRight(r)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public BasePanel onMenuPanelLoad(IPartialPageRequestHandler handler) {
		getBasePage().getHeader().setVisible(false);
		getMainPanel().getTopControls().setVisible(false);
		Component loader = getBasePage().getLoader().setVisible(false);
		if (r.isHidden(RoomElement.CHAT) || !isVisible()) {
			getMainPanel().getChat().toggle(handler, false);
		}
		if (handler != null) {
			handler.add(loader, getBasePage().getHeader(), getMainPanel().getTopControls());
			if (isVisible()) {
				handler.appendJavaScript("Room.load();");
			}
		}
		return this;
	}

	public void show(IPartialPageRequestHandler handler) {
		getMainPanel().getChat().toggle(handler, !r.isHidden(RoomElement.CHAT));
		handler.add(this.setVisible(true));
		handler.appendJavaScript("Room.load();");
	}

	@Override
	public void cleanup(IPartialPageRequestHandler handler) {
		if (eventDetail instanceof EventDetailDialog) {
			((EventDetailDialog)eventDetail).close(handler);
		}
		handler.add(getBasePage().getHeader().setVisible(true), getMainPanel().getTopControls().setVisible(true));
		if (r.isHidden(RoomElement.CHAT)) {
			getMainPanel().getChat().toggle(handler, true);
		}
		handler.appendJavaScript("if (typeof(Room) !== 'undefined') { Room.unload(); }");
		cm.exitRoom(getClient());
		getMainPanel().getChat().roomExit(r, handler);
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		response.render(new PriorityHeaderItem(JavaScriptHeaderItem.forReference(interview ? INTERVIEWWB_JS_REFERENCE : WB_JS_REFERENCE)));
		response.render(new PriorityHeaderItem(JavaScriptHeaderItem.forReference(new JavaScriptResourceReference(RoomPanel.class, "room.js"))));
		if (room.isVisible()) {
			response.render(OnDomReadyHeaderItem.forScript(roomEnter.getCallbackScript()));
		}
	}

	public void requestRight(Right right, IPartialPageRequestHandler handler) {
		RoomMessage.Type reqType = null;
		List<Client> mods = cm.listByRoom(r.getId(), c -> c.hasRight(Room.Right.MODERATOR));
		if (mods.isEmpty()) {
			if (r.isModerated()) {
				showIdeaAlert(handler, getString("696"));
				return;
			} else {
				// we found no-one we can ask, allow right
				rightsUpdated(cm.update(getClient().allow(right)));
			}
		}
		// ask
		switch (right) {
			case MODERATOR:
				reqType = Type.REQUEST_RIGHT_MODERATOR;
				break;
			case PRESENTER:
				reqType = Type.REQUEST_RIGHT_PRESENTER;
				break;
			case WHITEBOARD:
				reqType = Type.REQUEST_RIGHT_WB;
				break;
			case SHARE:
				reqType = Type.REQUEST_RIGHT_WB;
				break;
			case AUDIO:
				reqType = Type.REQUEST_RIGHT_A;
				break;
			case MUTE_OTHERS:
				reqType = Type.REQUEST_RIGHT_MUTE_OTHERS;
				break;
			case REMOTE_CONTROL:
				reqType = Type.REQUEST_RIGHT_REMOTE;
				break;
			case VIDEO:
				reqType = Type.REQUEST_RIGHT_AV;
				break;
			default:
				break;
		}
		if (reqType != null) {
			WebSocketHelper.sendRoom(new TextRoomMessage(getRoom().getId(), getClient(), reqType, getClient().getUid()));
		}
	}

	public void allowRight(Client client, Right... rights) {
		rightsUpdated(client.allow(rights));
	}

	public void denyRight(Client client, Right... rights) {
		for (Right right : rights) {
			client.deny(right);
		}
		if (client.hasActivity(Client.Activity.AUDIO) && !client.hasRight(Right.AUDIO)) {
			client.remove(Client.Activity.AUDIO);
		}
		if (client.hasActivity(Client.Activity.VIDEO) && !client.hasRight(Right.VIDEO)) {
			client.remove(Client.Activity.VIDEO);
		}
		rightsUpdated(client);
	}

	public void rightsUpdated(Client c) {
		cm.update(c);
		streamProcessor.rightsUpdated(c);
	}

	public void broadcast(Client client) {
		cm.update(client);
		WebSocketHelper.sendRoom(new TextRoomMessage(getRoom().getId(), getClient(), RoomMessage.Type.RIGHT_UPDATED, client.getUid()));
	}

	@Override
	protected void process(IPartialPageRequestHandler handler, JSONObject o) throws IOException {
		if (room.isVisible() && "room".equals(o.optString("area"))) {
			final String type = o.optString("type");
			if ("wb".equals(type)) {
				WbAction a = WbAction.valueOf(o.getString(PARAM_ACTION));
				wb.processWbAction(a, o.optJSONObject("data"), handler);
			} else if ("room".equals(type)) {
				sidebar.roomAction(handler, o);
			}
		}
	}

	public Room getRoom() {
		return r;
	}

	public Client getClient() {
		return getMainPanel().getClient();
	}

	public String getUid() {
		return getMainPanel().getUid();
	}

	public boolean screenShareAllowed() {
		Client c = getClient();
		return c.getScreenStream().isPresent() || streamProcessor.screenShareAllowed(c);
	}

	public RoomSidebar getSidebar() {
		return sidebar;
	}

	public AbstractWbPanel getWb() {
		return wb;
	}

	public String getPublishingUser() {
		return null;
	}

	public boolean isInterview() {
		return interview;
	}

	private void createWaitModerator(final boolean autoopen) {
		waitModerator = new Alert("wait-moderator", new ResourceModel("wait-moderator.message"), new ResourceModel("wait-moderator.title")) {
			private static final long serialVersionUID = 1L;

			@Override
			protected Component createMessage(String markupId, IModel<String> message) {
				return super.createMessage(markupId, message).setEscapeModelStrings(false);
			}
		};
		waitModerator.type(Alert.Type.Warning).setCloseButtonVisible(false);
		waitModerator.setOutputMarkupId(true).setOutputMarkupPlaceholderTag(true).setVisible(autoopen);
	}

	@Override
	protected String getCssClass() {
		String clazz = "room " + r.getType().name();
		if (r.isHidden(RoomElement.TOP_BAR)) {
			clazz += " no-menu";
		}
		if (r.isHidden(RoomElement.ACTIVITIES)) {
			clazz += " no-activities";
		}
		if (r.isHidden(RoomElement.CHAT)) {
			clazz += " no-chat";
		}
		if (!r.isHidden(RoomElement.MICROPHONE_STATUS)) {
			clazz += " mic-status";
		}
		return clazz;
	}

	private void showIdeaAlert(IPartialPageRequestHandler handler, String msg) {
		showAlert(handler, "info", msg, "far fa-lightbulb");
	}
	private void showAlert(IPartialPageRequestHandler handler, String type, String msg, String icon) {
		handler.appendJavaScript("OmUtil.alert('" + type + "', '<i class=\"" + icon + "\"></i>&nbsp;"
				+ StringEscapeUtils.escapeEcmaScript(msg)
				+ "', 10000)");
	}
}
