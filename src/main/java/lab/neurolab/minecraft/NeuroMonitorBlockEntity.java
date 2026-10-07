package lab.neurolab.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import java.util.UUID;
import java.util.List;

/** Rendering anchor for the client-side telemetry display. */
public final class NeuroMonitorBlockEntity extends BlockEntity {
    private static final String SUBJECT_TAG = "SubjectUUID";
    private UUID subject;
    private CompoundTag mindProfiles = new CompoundTag();
    private List<String> clientProfileNames = List.of();

    public NeuroMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(NeuroLabEntities.NEURO_MONITOR_ENTITY.get(), pos, state);
    }

    public UUID subject() { return subject; }
    public void bind(UUID subject) { this.subject = subject; sync(); }
    public void clearSubject() { subject = null; sync(); }

    public List<String> mindProfileNames() {
        return level != null && level.isClientSide ? clientProfileNames : List.copyOf(mindProfiles.getAllKeys());
    }
    public long[] mindProfile(String name) {
        return mindProfiles.contains(name, net.minecraft.nbt.Tag.TAG_LONG_ARRAY)
                ? mindProfiles.getLongArray(name) : null;
    }
    public boolean saveMindProfile(String name, long[] synapses) {
        if (name == null || !name.matches("[A-Za-z0-9 _-]{1,32}") || synapses.length > 8192) return false;
        if (!mindProfiles.contains(name) && mindProfiles.size() >= 16) return false;
        mindProfiles.putLongArray(name, synapses.clone());
        sync();
        return true;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (subject != null) tag.putUUID(SUBJECT_TAG, subject);
        tag.put("MindProfiles", mindProfiles.copy());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        subject = tag.hasUUID(SUBJECT_TAG) ? tag.getUUID(SUBJECT_TAG) : null;
        if (tag.contains("MindProfiles", net.minecraft.nbt.Tag.TAG_COMPOUND))
            mindProfiles = tag.getCompound("MindProfiles").copy();
        else if (level == null || !level.isClientSide) mindProfiles = new CompoundTag();
        if (tag.contains("MindProfileNames", net.minecraft.nbt.Tag.TAG_LIST)) {
            ListTag names = tag.getList("MindProfileNames", net.minecraft.nbt.Tag.TAG_STRING);
            clientProfileNames = names.stream().map(entry -> ((StringTag) entry).getAsString()).toList();
        }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.remove("MindProfiles");
        ListTag names = new ListTag();
        mindProfiles.getAllKeys().stream().sorted().forEach(name -> names.add(StringTag.valueOf(name)));
        tag.put("MindProfileNames", names);
        return tag;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
