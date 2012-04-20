package net.citizensnpcs.command.command;

import java.util.ArrayList;
import java.util.List;

import net.citizensnpcs.Citizens;
import net.citizensnpcs.Settings.Setting;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.character.Character;
import net.citizensnpcs.api.npc.character.CharacterManager;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.api.trait.trait.Owner;
import net.citizensnpcs.api.trait.trait.Spawned;
import net.citizensnpcs.command.Command;
import net.citizensnpcs.command.CommandContext;
import net.citizensnpcs.command.Requirements;
import net.citizensnpcs.command.exception.CommandException;
import net.citizensnpcs.command.exception.NoPermissionsException;
import net.citizensnpcs.npc.CitizensNPCManager;
import net.citizensnpcs.npc.CitizensTraitManager;
import net.citizensnpcs.trait.Age;
import net.citizensnpcs.trait.Controllable;
import net.citizensnpcs.trait.CurrentLocation;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.Powered;
import net.citizensnpcs.trait.Saddle;
import net.citizensnpcs.trait.VillagerProfession;
import net.citizensnpcs.trait.text.Text;
import net.citizensnpcs.util.Messaging;
import net.citizensnpcs.util.Paginator;
import net.citizensnpcs.util.StringHelper;

import org.bukkit.ChatColor;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

@Requirements(selected = true, ownership = true)
public class NPCCommands {
    private final CharacterManager characterManager = CitizensAPI.getCharacterManager();
    private final CitizensNPCManager npcManager;
    private final CitizensTraitManager traitManager = (CitizensTraitManager) CitizensAPI.getTraitManager();

    public NPCCommands(Citizens plugin) {
        npcManager = plugin.getNPCManager();
    }

    @Command(
            aliases = { "npc" },
            usage = "age [age] (-l)",
            desc = "Set the age of a NPC",
            flags = "l",
            modifiers = { "age" },
            min = 1,
            max = 2,
            permission = "npc.age")
    @Requirements(selected = true, ownership = true, types = { EntityType.CHICKEN, EntityType.COW, EntityType.OCELOT,
            EntityType.PIG, EntityType.SHEEP, EntityType.VILLAGER, EntityType.WOLF })
    public void age(CommandContext args, Player player, NPC npc) throws CommandException {
        Age trait = npc.getTrait(Age.class);

        if (args.argsLength() > 1) {
            int age = 0;
            String ageStr = "an adult";
            try {
                age = args.getInteger(1);
                if (age < -24000 || age > 0)
                    throw new CommandException("Invalid age. Valid: adult, baby, number between -24000 and 0");
                ageStr = "age " + age;
            } catch (NumberFormatException ex) {
                if (args.getString(1).equalsIgnoreCase("baby")) {
                    age = -24000;
                    ageStr = "a baby";
                } else if (!args.getString(1).equalsIgnoreCase("adult"))
                    throw new CommandException("Invalid age. Valid: adult, baby, number between -24000 and 0");
            }

            trait.setAge(age);
            Messaging.send(player, StringHelper.wrap(npc.getName()) + " is now " + ageStr + ".");
        }

        if (args.hasFlag('l'))
            Messaging.send(player, "<a>Age " + (trait.toggle() ? "locked" : "unlocked") + ".");
    }

    @Command(
            aliases = { "npc" },
            usage = "controllable",
            desc = "Toggles whether the NPC can be ridden and controlled",
            modifiers = { "controllable" },
            min = 2,
            max = 2,
            permission = "npc.controllable")
    public void toggleControllable(CommandContext args, Player player, NPC npc) {
        if (npc.hasTrait(Controllable.class)) {
            npc.removeTrait(Controllable.class);
            Messaging.send(player, StringHelper.wrap(npc.getName()) + " can no longer be controlled.");
        } else {
            npc.addTrait(traitManager.getTrait(Controllable.class, npc));
            Messaging.send(player, StringHelper.wrap(npc.getName()) + " can now be controlled.");
        }

    }

    @Command(
            aliases = { "npc" },
            usage = "character [character]",
            desc = "Set the character of a NPC",
            modifiers = { "character" },
            min = 2,
            max = 2)
    public void character(CommandContext args, Player player, NPC npc) throws CommandException {
        String name = args.getString(1).toLowerCase();
        Character character = characterManager.getCharacter(name);

        if (character == null)
            throw new CommandException("The character '" + args.getString(1) + "' does not exist.");
        if (npc.getCharacter() != null && npc.getCharacter().getName().equalsIgnoreCase(character.getName()))
            throw new CommandException("The NPC already has the character '" + name + "'.");
        if (!player.hasPermission("citizens.npc.character." + character.getName())
                && !player.hasPermission("citizens.npc.character.*") && !player.hasPermission("citizens.admin"))
            throw new NoPermissionsException();

        EntityType type = EntityType.valueOf(npc.getTrait(MobType.class).getType());
        if (!character.getValidTypes().isEmpty() && !character.getValidTypes().contains(type)) {
            Messaging.sendError(player, "This NPC cannot be given the character '" + character.getName() + "'.");
            return;
        }
        Messaging.send(player, StringHelper.wrap(npc.getName() + "'s") + " character is now " + StringHelper.wrap(name)
                + ".");
        npc.setCharacter(character);
    }

    @Command(
            aliases = { "npc" },
            usage = "create [name] ((-b) --type (type) --char (char))",
            desc = "Create a new NPC",
            flags = "b",
            modifiers = { "create" },
            min = 2,
            max = 5,
            permission = "npc.create")
    @Requirements
    public void create(CommandContext args, Player player, NPC npc) {
        String name = args.getString(1);
        if (name.length() > 16) {
            Messaging.sendError(player, "NPC names cannot be longer than 16 characters. The name has been shortened.");
            name = name.substring(0, 15);
        }
        EntityType type = EntityType.PLAYER;
        if (args.hasValueFlag("type")) {
            type = EntityType.fromName(args.getFlag("type"));
            if (type == null) {
                Messaging.sendError(player, "'" + args.getFlag("type")
                        + "' is not a valid mob type. Using default NPC.");
                type = EntityType.PLAYER;
            }
        }
        npc = npcManager.createNPC(type, name);
        String msg = ChatColor.GREEN + "You created " + StringHelper.wrap(npc.getName());
        if (args.hasValueFlag("char")) {
            String character = args.getFlag("char").toLowerCase();
            if (characterManager.getCharacter(character) == null) {
                Messaging.sendError(player, "'" + args.getFlag("char") + "' is not a valid character.");
                return;
            } else {
                Character set = characterManager.getCharacter(character);
                if (!set.getValidTypes().isEmpty() && !set.getValidTypes().contains(type)) {
                    Messaging.sendError(player, "The character '" + set.getName() + "' cannot be given the mob type '"
                            + type.name().toLowerCase() + "'.");
                    npc.remove();
                    return;
                }
                npc.setCharacter(characterManager.getCharacter(character));
                msg += " with the character " + StringHelper.wrap(character);
            }
        }
        msg += " at your location";

        int age = 0;
        if (args.hasFlag('b')) {
            if (!Ageable.class.isAssignableFrom(type.getEntityClass()))
                Messaging.sendError(player, "The mob type '" + type.name().toLowerCase().replace("_", "-")
                        + "' cannot be aged.");
            else {
                age = -24000;
                msg += " as a baby";
            }
        }

        msg += ".";

        // Initialize necessary traits
        npc.addTrait(traitManager.getTrait(Owner.class));
        if (!Setting.SERVER_OWNS_NPCS.asBoolean())
            npc.getTrait(Owner.class).setOwner(player.getName());
        npc.getTrait(MobType.class).setType(type.toString());
        npc.addTrait(traitManager.getTrait(LookClose.class, npc));
        npc.addTrait(traitManager.getTrait(Text.class, npc));
        npc.addTrait(traitManager.getTrait(Saddle.class, npc));

        npc.spawn(player.getLocation());

        // Set age after entity spawns
        npc.getTrait(Age.class).setAge(age);

        npcManager.selectNPC(player, npc);
        Messaging.send(player, msg);
    }

    @Command(
            aliases = { "npc" },
            usage = "despawn",
            desc = "Despawn a NPC",
            modifiers = { "despawn" },
            min = 1,
            max = 1,
            permission = "npc.despawn")
    public void despawn(CommandContext args, Player player, NPC npc) {
        npc.getTrait(Spawned.class).setSpawned(false);
        npc.despawn();
        Messaging.send(player, ChatColor.GREEN + "You despawned " + StringHelper.wrap(npc.getName()) + ".");
    }

    @Command(
            aliases = { "npc" },
            usage = "list (page) ((-a) --owner (owner) --type (type) --char (char))",
            desc = "List NPCs",
            flags = "a",
            modifiers = { "list" },
            min = 1,
            max = 2,
            permission = "npc.list")
    @Requirements
    public void list(CommandContext args, Player player, NPC npc) throws CommandException {
        List<NPC> npcs = new ArrayList<NPC>();

        if (args.hasFlag('a')) {
            for (NPC add : npcManager)
                npcs.add(add);
        } else if (args.getValueFlags().size() == 0 && args.argsLength() == 1 || args.argsLength() == 2) {
            for (NPC add : npcManager) {
                if (!npcs.contains(add) && add.getTrait(Owner.class).isOwner(player))
                    npcs.add(add);
            }
        } else {
            if (args.hasValueFlag("owner")) {
                String name = args.getFlag("owner");
                for (NPC add : npcManager) {
                    if (!npcs.contains(add) && add.getTrait(Owner.class).getOwner().equals(name))
                        npcs.add(add);
                }
            }

            if (args.hasValueFlag("type")) {
                String type = args.getFlag("type");

                if (EntityType.fromName(type.replace('-', '_')) == null)
                    throw new CommandException("'" + type + "' is not a valid mob type.");

                for (NPC add : npcManager) {
                    if (!npcs.contains(add) && add.getTrait(MobType.class).getType().equalsIgnoreCase(type))
                        npcs.add(add);
                }
            }

            if (args.hasValueFlag("char")) {
                String character = args.getFlag("char");
                if (characterManager.getCharacter(character) == null)
                    throw new CommandException("'" + character + "' is not a valid character.");

                for (NPC add : npcManager.getNPCs(characterManager.getCharacter(character).getClass())) {
                    if (!npcs.contains(add))
                        npcs.add(add);
                }
            }
        }

        Paginator paginator = new Paginator().header("NPCs");
        paginator.addLine("<e>Key: <a>ID  <b>Name");
        for (int i = 0; i < npcs.size(); i += 2) {
            String line = "<a>" + npcs.get(i).getId() + "<b>  " + npcs.get(i).getName();
            if (npcs.size() >= i + 2)
                line += "      " + "<a>" + npcs.get(i + 1).getId() + "<b>  " + npcs.get(i + 1).getName();
            paginator.addLine(line);
        }

        int page = args.getInteger(1, 1);
        if (!paginator.sendPage(player, page))
            throw new CommandException("The page '" + page + "' does not exist.");
    }

    @Command(
            aliases = { "npc" },
            usage = "lookclose",
            desc = "Toggle whether a NPC will look when a player is near",
            modifiers = { "lookclose", "look", "rotate" },
            min = 1,
            max = 1,
            permission = "npc.lookclose")
    public void lookClose(CommandContext args, Player player, NPC npc) {
        String msg = StringHelper.wrap(npc.getName()) + " will "
                + (npc.getTrait(LookClose.class).toggle() ? "now rotate" : "no longer rotate");
        Messaging.send(player, msg + " when a player is nearby.");
    }

    @Command(aliases = { "npc" }, desc = "Show basic NPC information", max = 0)
    public void npc(CommandContext args, Player player, NPC npc) {
        Messaging.send(player, StringHelper.wrapHeader(npc.getName()));
        Messaging.send(player, "    <a>ID: <e>" + npc.getId());
        Messaging.send(player, "    <a>Character: <e>"
                + (npc.getCharacter() != null ? npc.getCharacter().getName() : "None"));
        Messaging.send(player, "    <a>Type: <e>" + npc.getTrait(MobType.class).getType());
    }

    @Command(
            aliases = { "npc" },
            usage = "owner [name]",
            desc = "Set the owner of an NPC",
            modifiers = { "owner" },
            min = 1,
            max = 2,
            permission = "npc.owner")
    public void owner(CommandContext args, Player player, NPC npc) throws CommandException {
        if (args.argsLength() == 1) {
            Messaging.send(player, StringHelper.wrap(npc.getName() + "'s Owner: ")
                    + npc.getTrait(Owner.class).getOwner());
            return;
        }
        String name = args.getString(1);
        if (npc.getTrait(Owner.class).getOwner().equals(name))
            throw new CommandException("'" + name + "' is already the owner of " + npc.getName() + ".");
        npc.getTrait(Owner.class).setOwner(name.equalsIgnoreCase("server") ? "server" : name);
        Messaging.send(player, (name.equalsIgnoreCase("server") ? "<a>The server" : StringHelper.wrap(name))
                + " is now the owner of " + StringHelper.wrap(npc.getName()) + ".");
    }

    @Command(
            aliases = { "npc" },
            usage = "power",
            desc = "Toggle a creeper NPC as powered",
            modifiers = { "power" },
            min = 1,
            max = 1,
            permission = "npc.power")
    @Requirements(selected = true, ownership = true, types = { EntityType.CREEPER })
    public void power(CommandContext args, Player player, NPC npc) {
        String msg = StringHelper.wrap(npc.getName()) + " will "
                + (npc.getTrait(Powered.class).toggle() ? "now" : "no longer");
        Messaging.send(player, msg += " be powered.");
    }

    @Command(
            aliases = { "npc" },
            usage = "profession [profession]",
            desc = "Set a NPC's profession",
            modifiers = { "profession" },
            min = 2,
            max = 2,
            permission = "npc.profession")
    @Requirements(selected = true, ownership = true, types = { EntityType.VILLAGER })
    public void profession(CommandContext args, Player player, NPC npc) throws CommandException {
        String profession = args.getString(1);
        try {
            npc.getTrait(VillagerProfession.class).setProfession(Profession.valueOf(profession.toUpperCase()));
            Messaging.send(
                    player,
                    StringHelper.wrap(npc.getName()) + " is now the profession "
                            + StringHelper.wrap(profession.toUpperCase()) + ".");
        } catch (IllegalArgumentException ex) {
            throw new CommandException("'" + profession + "' is not a valid profession.");
        }
    }

    @Command(
            aliases = { "npc" },
            usage = "remove (all)",
            desc = "Remove a NPC",
            modifiers = { "remove" },
            min = 1,
            max = 2)
    @Requirements
    public void remove(CommandContext args, Player player, NPC npc) throws CommandException {
        if (args.argsLength() == 2) {
            if (!args.getString(1).equals("all"))
                throw new CommandException("Incorrect syntax. /npc remove (all)");
            if (!player.hasPermission("citizens.npc.remove.all") && !player.hasPermission("citizens.admin"))
                throw new NoPermissionsException();
            npcManager.removeAll();
            Messaging.send(player, "<a>You permanently removed all NPCs.");
            return;
        }
        if (npc == null)
            throw new CommandException("You must have an NPC selected to execute that command.");
        if (!npc.getTrait(Owner.class).isOwner(player))
            throw new CommandException("You must be the owner of this NPC to execute that command.");
        if (!player.hasPermission("citizens.npc.remove") && !player.hasPermission("citizens.admin"))
            throw new NoPermissionsException();
        npc.remove();
        Messaging.send(player, "<a>You permanently removed " + StringHelper.wrap(npc.getName()) + ".");
    }

    @Command(
            aliases = { "npc" },
            usage = "rename [name]",
            desc = "Rename a NPC",
            modifiers = { "rename" },
            min = 2,
            max = 2,
            permission = "npc.rename")
    public void rename(CommandContext args, Player player, NPC npc) {
        String oldName = npc.getName();
        String newName = args.getString(1);
        if (newName.length() > 16) {
            Messaging.sendError(player, "NPC names cannot be longer than 16 characters. The name has been shortened.");
            newName = newName.substring(0, 15);
        }
        npc.setName(newName);
        Messaging.send(player,
                ChatColor.GREEN + "You renamed " + StringHelper.wrap(oldName) + " to " + StringHelper.wrap(newName)
                        + ".");
    }

    @Command(
            aliases = { "npc" },
            usage = "select [id]",
            desc = "Select a NPC with the given ID",
            modifiers = { "select" },
            min = 2,
            max = 2,
            permission = "npc.select")
    @Requirements(ownership = true)
    public void select(CommandContext args, Player player, NPC npc) throws CommandException {
        NPC toSelect = npcManager.getNPC(args.getInteger(1));
        if (toSelect == null || !toSelect.getTrait(Spawned.class).shouldSpawn())
            throw new CommandException("No NPC with the ID '" + args.getInteger(1) + "' is spawned.");
        if (npc != null && toSelect.getId() == npc.getId())
            throw new CommandException("You already have that NPC selected.");
        npcManager.selectNPC(player, toSelect);
        Messaging.sendWithNPC(player, Setting.SELECTION_MESSAGE.asString(), toSelect);
    }

    @Command(
            aliases = { "npc" },
            usage = "spawn [id]",
            desc = "Spawn an existing NPC",
            modifiers = { "spawn" },
            min = 2,
            max = 2,
            permission = "npc.spawn")
    @Requirements
    public void spawn(CommandContext args, Player player, NPC npc) throws CommandException {
        NPC respawn = npcManager.getNPC(args.getInteger(1));
        if (respawn == null)
            throw new CommandException("No NPC with the ID '" + args.getInteger(1) + "' exists.");

        if (!respawn.getTrait(Owner.class).isOwner(player))
            throw new CommandException("You must be the owner of this NPC to execute that command.");

        if (respawn.spawn(player.getLocation())) {
            npcManager.selectNPC(player, respawn);
            Messaging.send(player, ChatColor.GREEN + "You respawned " + StringHelper.wrap(respawn.getName())
                    + " at your location.");
        } else
            throw new CommandException(respawn.getName() + " is already spawned at another location."
                    + " Use '/npc tphere' to teleport the NPC to your location.");
    }

    @Command(
            aliases = { "npc" },
            usage = "tp",
            desc = "Teleport to a NPC",
            modifiers = { "tp", "teleport" },
            min = 1,
            max = 1,
            permission = "npc.tp")
    public void tp(CommandContext args, Player player, NPC npc) {
        // Spawn the NPC if it isn't spawned to prevent NPEs
        if (!npc.isSpawned())
            npc.spawn(npc.getTrait(CurrentLocation.class).getLocation());
        player.teleport(npc.getBukkitEntity(), TeleportCause.COMMAND);
        Messaging.send(player, ChatColor.GREEN + "You teleported to " + StringHelper.wrap(npc.getName()) + ".");
    }

    @Command(
            aliases = { "npc" },
            usage = "tphere",
            desc = "Teleport a NPC to your location",
            modifiers = { "tphere" },
            min = 1,
            max = 1,
            permission = "npc.tphere")
    public void tphere(CommandContext args, Player player, NPC npc) {
        // Spawn the NPC if it isn't spawned to prevent NPEs
        if (!npc.isSpawned())
            npc.spawn(npc.getTrait(CurrentLocation.class).getLocation());
        npc.getBukkitEntity().teleport(player, TeleportCause.COMMAND);
        Messaging.send(player, StringHelper.wrap(npc.getName()) + " was teleported to your location.");
    }
}