package com.pengin0503.scheduleddimensionlock;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pengin0503.scheduleddimensionlock.core.DateTimeParser;
import com.pengin0503.scheduleddimensionlock.core.DimensionState;
import com.pengin0503.scheduleddimensionlock.core.DurationParser;
import com.pengin0503.scheduleddimensionlock.core.ScheduleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

public final class SdlCommands {
    private SdlCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(buildRoot("sdl"));
        dispatcher.register(buildRoot("scheduleddimensionlock"));
    }

    private static boolean hasOpPermission(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String rootName) {
        return Commands.literal(rootName)
                .then(Commands.literal("status")
                        .executes(SdlCommands::statusAll)
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(SdlCommands::statusOne)))
                .then(Commands.literal("lock")
                        .requires(SdlCommands::hasOpPermission)
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(SdlCommands::lockDimension)))
                .then(Commands.literal("unlock")
                        .requires(SdlCommands::hasOpPermission)
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(SdlCommands::unlockDimension)))
                .then(Commands.literal("schedule")
                        .requires(SdlCommands::hasOpPermission)
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .then(Commands.literal("unlock")
                                        .then(Commands.literal("at")
                                                .then(Commands.argument("datetime", StringArgumentType.greedyString())
                                                        .executes(SdlCommands::scheduleUnlockAt)))
                                        .then(Commands.literal("in")
                                                .then(Commands.argument("duration", StringArgumentType.word())
                                                        .executes(SdlCommands::scheduleUnlockIn))))
                                .then(Commands.literal("lock")
                                        .then(Commands.literal("at")
                                                .then(Commands.argument("datetime", StringArgumentType.greedyString())
                                                        .executes(SdlCommands::scheduleLockAt)))
                                        .then(Commands.literal("in")
                                                .then(Commands.argument("duration", StringArgumentType.word())
                                                        .executes(SdlCommands::scheduleLockIn))))
                                .then(Commands.literal("window")
                                        .then(Commands.argument("window", StringArgumentType.greedyString())
                                                .executes(SdlCommands::scheduleWindow)))
                                .then(Commands.literal("clear")
                                        .executes(SdlCommands::scheduleClear))))
                .then(Commands.literal("evacuate")
                        .requires(SdlCommands::hasOpPermission)
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(SdlCommands::evacuateDimension)))
                .then(Commands.literal("reload")
                        .requires(SdlCommands::hasOpPermission)
                        .executes(SdlCommands::reloadConfig));
    }

    private static String getDimensionId(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = DimensionArgument.getDimension(ctx, "dimension");
        return level.dimension().identifier().toString();
    }

    private static int statusAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        ScheduleManager manager = SdlSavedData.get(server).getManager();
        ZoneId zone = SdlConfig.getZoneId();
        Instant now = Instant.now();

        source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.status.header", zone.getId())
                .withStyle(ChatFormatting.AQUA), false);

        Map<String, DimensionState> all = manager.getAllStates();
        if (all.isEmpty()) {
            source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.status.empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        for (Map.Entry<String, DimensionState> entry : all.entrySet()) {
            String dimId = entry.getKey();
            DimensionState state = entry.getValue();
            Component stateLabel = state.isLocked()
                    ? SdlMessages.text("command.scheduleddimensionlock.status.state.locked").withStyle(ChatFormatting.RED)
                    : SdlMessages.text("command.scheduleddimensionlock.status.state.unlocked").withStyle(ChatFormatting.GREEN);

            Optional<ScheduleManager.NextTransition> next = manager.getNextTransition(dimId);
            if (next.isPresent()) {
                ScheduleManager.NextTransition transition = next.get();
                String formattedTime = DateTimeParser.format(transition.time(), zone);
                String remaining = DurationParser.format(Duration.between(now, transition.time()));
                String key = transition.type() == ScheduleManager.TransitionType.UNLOCKED
                        ? "command.scheduleddimensionlock.status.line_unlock"
                        : "command.scheduleddimensionlock.status.line_lock";
                source.sendSuccess(() -> SdlMessages.text(key, dimId, stateLabel, formattedTime, remaining), false);
            } else {
                source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.status.line", dimId, stateLabel), false);
            }
        }
        return 1;
    }

    private static int statusOne(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String dimId = getDimensionId(ctx);
        ScheduleManager manager = SdlSavedData.get(source.getServer()).getManager();
        ZoneId zone = SdlConfig.getZoneId();
        Instant now = Instant.now();

        DimensionState state = manager.getState(dimId)
                .orElse(new DimensionState(false, null, null, null));

        source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.status.detail.header", dimId, zone.getId())
                .withStyle(ChatFormatting.AQUA), false);

        Component stateLabel = state.isLocked()
                ? SdlMessages.text("command.scheduleddimensionlock.status.state.locked").withStyle(ChatFormatting.RED)
                : SdlMessages.text("command.scheduleddimensionlock.status.state.unlocked").withStyle(ChatFormatting.GREEN);
        source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.status.detail.state", stateLabel), false);

        boolean hasAny = false;
        if (state.getUnlockAt() != null) {
            hasAny = true;
            String time = DateTimeParser.format(state.getUnlockAt(), zone);
            String remaining = DurationParser.format(Duration.between(now, state.getUnlockAt()));
            source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.status.detail.unlock_at", time, remaining), false);
        }
        if (state.getLockAt() != null) {
            hasAny = true;
            String time = DateTimeParser.format(state.getLockAt(), zone);
            String remaining = DurationParser.format(Duration.between(now, state.getLockAt()));
            source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.status.detail.lock_at", time, remaining), false);
        }
        if (!hasAny) {
            source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.status.detail.none")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int lockDimension(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String dimId = getDimensionId(ctx);
        if (ScheduleManager.OVERWORLD_ID.equals(dimId)) {
            source.sendFailure(SdlMessages.text("command.scheduleddimensionlock.error.overworld"));
            return 0;
        }
        MinecraftServer server = source.getServer();
        SdlSavedData data = SdlSavedData.get(server);
        data.getManager().lock(dimId);
        data.setDirty();

        int evacuated = SdlConfig.isEvacuateOnLock() ? SdlService.evacuateDimension(server, dimId) : 0;
        source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.lock.success", dimId, evacuated), true);
        return 1;
    }

    private static int unlockDimension(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String dimId = getDimensionId(ctx);
        if (ScheduleManager.OVERWORLD_ID.equals(dimId)) {
            source.sendFailure(SdlMessages.text("command.scheduleddimensionlock.error.overworld"));
            return 0;
        }
        MinecraftServer server = source.getServer();
        SdlSavedData data = SdlSavedData.get(server);
        data.getManager().unlock(dimId);
        data.setDirty();

        source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.unlock.success", dimId), true);
        boolean hasSchedule = data.getManager().getState(dimId).map(DimensionState::hasSchedule).orElse(false);
        if (hasSchedule) {
            source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.unlock.schedule_retained", dimId)
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int scheduleUnlockAt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String dimId = getDimensionId(ctx);
        if (ScheduleManager.OVERWORLD_ID.equals(dimId)) {
            source.sendFailure(SdlMessages.text("command.scheduleddimensionlock.error.overworld"));
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, "datetime");
        ZoneId zone = SdlConfig.getZoneId();
        try {
            Instant target = DateTimeParser.parse(raw, zone);
            return applyScheduleUnlock(source, dimId, target, zone);
        } catch (IllegalArgumentException e) {
            source.sendFailure(SdlMessages.text("command.scheduleddimensionlock.error.invalid_argument", e.getMessage()));
            return 0;
        }
    }

    private static int scheduleUnlockIn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String dimId = getDimensionId(ctx);
        if (ScheduleManager.OVERWORLD_ID.equals(dimId)) {
            source.sendFailure(SdlMessages.text("command.scheduleddimensionlock.error.overworld"));
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, "duration");
        ZoneId zone = SdlConfig.getZoneId();
        try {
            Duration duration = DurationParser.parse(raw);
            Instant target = Instant.now().plus(duration);
            return applyScheduleUnlock(source, dimId, target, zone);
        } catch (IllegalArgumentException e) {
            source.sendFailure(SdlMessages.text("command.scheduleddimensionlock.error.invalid_argument", e.getMessage()));
            return 0;
        }
    }

    private static int applyScheduleUnlock(CommandSourceStack source, String dimId, Instant target, ZoneId zone) {
        MinecraftServer server = source.getServer();
        SdlSavedData data = SdlSavedData.get(server);
        data.getManager().scheduleUnlock(dimId, target);
        data.setDirty();
        if (SdlConfig.isEvacuateOnLock()) {
            SdlService.evacuateDimension(server, dimId);
        }
        String formatted = DateTimeParser.format(target, zone);
        String remaining = DurationParser.format(Duration.between(Instant.now(), target));
        source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.schedule.unlock_set", dimId, formatted, remaining), true);
        return 1;
    }

    private static int scheduleLockAt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String dimId = getDimensionId(ctx);
        if (ScheduleManager.OVERWORLD_ID.equals(dimId)) {
            source.sendFailure(SdlMessages.text("command.scheduleddimensionlock.error.overworld"));
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, "datetime");
        ZoneId zone = SdlConfig.getZoneId();
        try {
            Instant target = DateTimeParser.parse(raw, zone);
            return applyScheduleLock(source, dimId, target, zone);
        } catch (IllegalArgumentException e) {
            source.sendFailure(SdlMessages.text("command.scheduleddimensionlock.error.invalid_argument", e.getMessage()));
            return 0;
        }
    }

    private static int scheduleLockIn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String dimId = getDimensionId(ctx);
        if (ScheduleManager.OVERWORLD_ID.equals(dimId)) {
            source.sendFailure(SdlMessages.text("command.scheduleddimensionlock.error.overworld"));
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, "duration");
        ZoneId zone = SdlConfig.getZoneId();
        try {
            Duration duration = DurationParser.parse(raw);
            Instant target = Instant.now().plus(duration);
            return applyScheduleLock(source, dimId, target, zone);
        } catch (IllegalArgumentException e) {
            source.sendFailure(SdlMessages.text("command.scheduleddimensionlock.error.invalid_argument", e.getMessage()));
            return 0;
        }
    }

    private static int applyScheduleLock(CommandSourceStack source, String dimId, Instant target, ZoneId zone) {
        MinecraftServer server = source.getServer();
        SdlSavedData data = SdlSavedData.get(server);
        data.getManager().scheduleLock(dimId, target);
        data.setDirty();
        String formatted = DateTimeParser.format(target, zone);
        String remaining = DurationParser.format(Duration.between(Instant.now(), target));
        source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.schedule.lock_set", dimId, formatted, remaining), true);
        return 1;
    }

    private static int scheduleWindow(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String dimId = getDimensionId(ctx);
        if (ScheduleManager.OVERWORLD_ID.equals(dimId)) {
            source.sendFailure(SdlMessages.text("command.scheduleddimensionlock.error.overworld"));
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, "window");
        ZoneId zone = SdlConfig.getZoneId();
        try {
            DateTimeParser.Window window = DateTimeParser.parseWindow(raw, zone);
            MinecraftServer server = source.getServer();
            SdlSavedData data = SdlSavedData.get(server);
            data.getManager().scheduleWindow(dimId, window.unlockAt(), window.lockAt());
            data.setDirty();
            if (SdlConfig.isEvacuateOnLock()) {
                SdlService.evacuateDimension(server, dimId);
            }
            String unlockStr = DateTimeParser.format(window.unlockAt(), zone);
            String lockStr = DateTimeParser.format(window.lockAt(), zone);
            source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.schedule.window_set", dimId, unlockStr, lockStr), true);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(SdlMessages.text("command.scheduleddimensionlock.error.invalid_argument", e.getMessage()));
            return 0;
        }
    }

    private static int scheduleClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String dimId = getDimensionId(ctx);
        if (ScheduleManager.OVERWORLD_ID.equals(dimId)) {
            source.sendFailure(SdlMessages.text("command.scheduleddimensionlock.error.overworld"));
            return 0;
        }
        SdlSavedData data = SdlSavedData.get(source.getServer());
        boolean cleared = data.getManager().clearSchedule(dimId);
        if (cleared) {
            data.setDirty();
            source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.schedule.cleared", dimId), true);
        } else {
            source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.schedule.nothing_to_clear", dimId), false);
        }
        return 1;
    }

    private static int evacuateDimension(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String dimId = getDimensionId(ctx);
        if (ScheduleManager.OVERWORLD_ID.equals(dimId)) {
            source.sendFailure(SdlMessages.text("command.scheduleddimensionlock.error.overworld"));
            return 0;
        }
        int evacuated = SdlService.evacuateDimension(source.getServer(), dimId);
        source.sendSuccess(() -> SdlMessages.text("command.scheduleddimensionlock.evacuate.success", evacuated, dimId), true);
        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        SdlConfig.refreshCache();
        ZoneId zone = SdlConfig.getZoneId();
        ctx.getSource().sendSuccess(
                () -> SdlMessages.text("command.scheduleddimensionlock.reload.success", zone.getId()),
                true
        );
        return 1;
    }
}
