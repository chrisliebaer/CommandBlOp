# WARNING
Command blocks allow everyone that has access to run commands as the console user. THIS MEANS EVERYTHING! This plugin will not prevent that. **It does not provide improved security over giving every player operator permission!** You have been warned.

# What does this do?
Current Minecraft does not allow you to use command blocks if you are not an operator on the server. Since this is hardcoded into the Mojang portion of the server, modded servers are unable to override this behavior. The issues is compicated by the fact that the Minecraft client also refuses to even try to interfact with commands blocks since it knows it's current op state.

This plugin uses [ProtocolLib](https://github.com/aadnk/ProtocolLib) to send an fake operator status to the client. Likewise, if the client is tricked into assuming that it is an operation. The plugin will intercept incoming packets and perform the required updates by itself.

# Requirements
* [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) for packet interception
* [NBT API](https://www.spigotmc.org/resources/nbt-api.7939/) for accessing command block nbt data
* Right now, this plugin has only been tested with Minecraft 1.14

# Why would you need that?
Bukkit and forks will give certain permissions to every player with operator status. While this might seem like a good idea. It's horrible. You can't control which permissions a player gets. They get all default permissions. These include bypasses or commands you really don't want on your server. Even worse: If often includes bypass permissions that you don't even know are absence from your regular permission system since you always have them. This creates hard to debug permission problems.

# Permissions
Keep in mind that you have to be in creative mode in order to interact with command blocks. This is a Minecraft limitation. Likewise any real operator on the server is not impacted by this plugin or it's permissions at all.

* `commandblop.fakeop` - Send fake op level to client. Requires reconnect. Must be set for all other permissions!
* `commandblop.break` - Allows player to break command blocks without op.
* `commandblop.view` - Allows player to view command set in command block without op.
* `commandblop.edit` - Allows player to edit command block without op. 
* `commandblop.place` - Allowy player to place command blocks without op.

# Limitations
The only limitation I'm aware right now is that when placing an command block with NBT data from your hand, the NBT data will not be applied.

# Bugs & Features
I really don't care about bug reports. I might not be updating this plugin to newer versions. You are welcome to provide pull requests. If you truly think that there is a bug that should be addressed, feel free to create an issue. Just keep in mind that I'm not going to ask for more details. If you fail to provide the absolute basics for a meaningfull bug report, I'm just going to ignore it.
