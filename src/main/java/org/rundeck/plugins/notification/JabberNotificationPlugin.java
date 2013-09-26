package org.rundeck.plugins.notification;

import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.PropertyScope;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription;
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty;
import com.dtolabs.rundeck.plugins.notification.NotificationPlugin;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.muc.MultiUserChat;

import java.util.Map;

/**
 * Jabber Notification plugin
 */
@Plugin(name = JabberNotificationPlugin.NAME, service = ServiceNameConstants.Notification)
@PluginDescription(title = "Jabber", description = "Notify a Jabber ID or Chat room")
public class JabberNotificationPlugin implements NotificationPlugin {
    public static final String NAME = "jabber-xmpp";
    private static final String DEFAULT_RESOURCE_NAME = "rundeck";
    private static final String DEFAULT_DELAY = "10000";


    @PluginProperty(title = "Hostname", description = "Jabber server hostname", scope = PropertyScope.Project)
    private String hostname;
    @PluginProperty(title = "Port", description = "Jabber server port", defaultValue = "5222",
            scope = PropertyScope.Project)
    private int port;
    @PluginProperty(title = "Username", description = "Username", scope = PropertyScope.Project)
    private String username;
    @PluginProperty(title = "Password", description = "Password", scope = PropertyScope.Project)
    private String password;
    @PluginProperty(title = "Resource name", description = "Name of the resource used when logging in, " +
            "default: (rundeck)",
            defaultValue = DEFAULT_RESOURCE_NAME)
    private String resourceName;

    @PluginProperty(title = "Jabber ID", description = "Jabber ID of the message destination")
    private String jabberId;
    @PluginProperty(title = "Disconnect Delay", description = "Delay after sending messages to wait before " +
            "disconnecting. Default: (10000ms).", defaultValue = DEFAULT_DELAY, scope = PropertyScope.Project)
    private long disconnectDelay;
    @PluginProperty(title = "Chat Room Jabber ID", description = "Jabber ID of a mult-user Chat room")
    private String chatroomJabberId;
    @PluginProperty(title = "Chat Room Nickname", description = "Nickname to use in the chat room",
            defaultValue = "rundeck")
    private String chatroomNickname;
    @PluginProperty(title = "Chat Room Password", description = "Password to connect to the chat room")
    private String chatroomPassword;


    @Override
    public String toString() {
        return "JabberNotificationPlugin{" +
                "hostname='" + hostname + '\'' +
                ", port=" + port +
                ", username='" + username + '\'' +
                ", password='" + sanitize(password) + '\'' +
                ", resourceName='" + resourceName + '\'' +
                ", jabberId='" + jabberId + '\'' +
                ", disconnectDelay=" + disconnectDelay +
                ", chatroomJabberId='" + chatroomJabberId + '\'' +
                ", chatroomNickname='" + chatroomNickname + '\'' +
                ", chatroomPassword='" + sanitize(chatroomPassword) + '\'' +
                '}';
    }

    private String sanitize(String password1) {
        return null != password1 && !"".equals(password1) ? "***" : "(unset)";
    }

    @Override
    public boolean postNotification(String trigger, Map executionData, Map configuration) {
        if (null == hostname) {
            throw new IllegalStateException("hostname is required");
        }
        if (0 == port) {
            throw new IllegalStateException("port is required");
        }
        if (isBlank(jabberId) && isBlank(chatroomJabberId)) {
            throw new IllegalStateException("jabberId or chatroomJabberId must be set");
        }
        if (null == username) {
            throw new IllegalStateException("username is required");
        }
        if (null == password) {
            throw new IllegalStateException("password is required");
        }
        if (isBlank(resourceName)) {
            resourceName = DEFAULT_RESOURCE_NAME;
        }

        ConnectionConfiguration config = new ConnectionConfiguration(hostname, port);
        config.setCompressionEnabled(true);
        config.setSASLAuthenticationEnabled(true);

        XMPPConnection xmppConnection = new XMPPConnection(config);
        try {
            xmppConnection.connect();
            xmppConnection.login(username, password, resourceName);
            //send notification message
            if (!isBlank(jabberId)) {
                sendSingleUserChat(trigger, executionData, configuration, xmppConnection);
            } else if (!isBlank(chatroomJabberId)) {
                sendMultiUserChat(trigger, executionData, configuration, xmppConnection);
            }
        } catch (XMPPException e) {
            e.printStackTrace();
            return false;
        } finally {
            waitThenDisconnect(xmppConnection);
        }

        return true;
    }

    private void waitThenDisconnect(final XMPPConnection xmppConnection) {
        if (disconnectDelay > 0) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(disconnectDelay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    xmppConnection.disconnect();
                }
            }.start();
        }
    }

    /**
     * Send message to single user
     * @param trigger
     * @param executionData
     * @param configuration
     * @param connection
     * @throws XMPPException
     */
    private void sendSingleUserChat(String trigger, Map executionData, Map configuration, XMPPConnection
            connection) throws XMPPException {
        ChatManager chatmanager = connection.getChatManager();
        Chat newChat = chatmanager.createChat(jabberId, new MessageListener() {
            public void processMessage(Chat chat, Message message) {
            }
        });

        newChat.sendMessage(generateMessage(trigger, executionData));
    }

    /**
     * Send message to chat room
     * @param trigger
     * @param executionData
     * @param configuration
     * @param connection
     * @throws XMPPException
     */
    private void sendMultiUserChat(String trigger, Map executionData, Map configuration, XMPPConnection
            connection) throws XMPPException {
        MultiUserChat muc2 = new MultiUserChat(connection, chatroomJabberId);
        // join with password if set
        if (isBlank(chatroomPassword)) {
            muc2.join(chatroomNickname);
        } else {
            muc2.join(chatroomNickname, chatroomPassword);
        }

        muc2.sendMessage(generateMessage(trigger, executionData));
    }

    private boolean isBlank(String string) {
        return null == string || "".equals(string);
    }

    /**
     * Format the message to send
     * @param trigger
     * @param executionData
     * @return
     */
    private String generateMessage(String trigger, Map executionData) {
        Object job = executionData.get("job");
        Map jobdata = (Map) job;
        Object groupPath = jobdata.get("group");
        Object jobname = jobdata.get("name");
        String jobdesc = (!isBlank(groupPath.toString()) ? groupPath + "/" : "") + jobname;

        return "[" + trigger.toUpperCase() + "] " + jobdesc + " run by " + executionData.get("user") + ": " +
                executionData.get("href");
    }
}
