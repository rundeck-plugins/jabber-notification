Jabber Notification Plugin for Rundeck
-----------

Notifies a Jabber ID or a Multi-user Chat room about Job status.

Install the plugin in `libext/`

Configuration
-----------

Modify your `project.properties`:

    project.plugin.Notification.jabber-xmpp.hostname=jabber.company.com
    project.plugin.Notification.jabber-xmpp.port=5222
    project.plugin.Notification.jabber-xmpp.username=user
    project.plugin.Notification.jabber-xmpp.password=password
    project.plugin.Notification.jabber-xmpp.resourceName=rundeck

    # specify a user jabberId if not using chat room
    #project.plugin.Notification.jabber-xmpp.jabberId=user@jabber.company.com

    # specify a chat room Id if using chat room
    project.plugin.Notification.jabber-xmpp.chatroomJabberId=room@conference.jabber.company.com
    project.plugin.Notification.jabber-xmpp.chatroomNickname=my nickname

    # if necessary use a password
    project.plugin.Notification.jabber-xmpp.chatroomPassword=password


Alternately modify `framework.properties`, but replace "project." with "framework.".
