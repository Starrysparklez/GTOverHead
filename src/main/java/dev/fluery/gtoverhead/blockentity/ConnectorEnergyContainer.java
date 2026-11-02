package dev.fluery.gtoverhead.blockentity;

import dev.fluery.gtoverhead.GTOverheadConfig;
import dev.fluery.gtoverhead.tier.WireTier;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;

import net.minecraft.core.Direction;

public class ConnectorEnergyContainer implements IEnergyContainer {

    private final ConnectorBlockEntity connector;
    private long energyStored;
    private long wireOriginEnergy;

    public ConnectorEnergyContainer(ConnectorBlockEntity connector) {
        this.connector = connector;
    }

    private WireTier tier() {
        return connector.getTier();
    }

    @Override
    public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
        if (voltage <= 0) {
            return 0;
        }
        if (voltage > getInputVoltage()) {
            // overvoltage from the local network: standard GTCEu explosion behaviour
            connector.explode(voltage);
            return Math.min(amperage, getInputAmperage());
        }
        return insertEnergy(voltage, amperage);
    }

    /**
     * Energy pushed through the overhead wire. Overvoltage was already checked by the
     * sending side, so no explosion here, but received energy is flagged as wire-origin
     * so it cannot be sent straight back across the same wire.
     */
    public long acceptWireEnergy(long voltage, long amperage) {
        return insertEnergy(voltage, amperage);
    }

    private long insertEnergy(long voltage, long amperage) {
        long canAccept = getEnergyCapacity() - energyStored;
        if (canAccept >= voltage) {
            long accepted = Math.min(canAccept / voltage, Math.min(amperage, getInputAmperage()));
            if (accepted > 0) {
                energyStored += accepted * voltage;
                connector.setChanged();
                return accepted;
            }
        }
        return 0;
    }

    /**
     * Amount of energy currently held in the buffer that arrived through the wire.
     * This energy may only be delivered to the local network, never sent back.
     */
    public long getWireOriginEnergy() {
        return Math.min(wireOriginEnergy, energyStored);
    }

    public void addWireOriginEnergy(long energy) {
        this.wireOriginEnergy = Math.min(getEnergyCapacity(), this.wireOriginEnergy + energy);
    }

    public void onEnergyDrained(long energy) {
        this.wireOriginEnergy = Math.max(0, this.wireOriginEnergy - energy);
    }

    public void clearWireOriginEnergy() {
        this.wireOriginEnergy = 0;
    }

    @Override
    public boolean inputsEnergy(Direction side) {
        return getInputVoltage() > 0 && getInputAmperage() > 0;
    }

    @Override
    public boolean outputsEnergy(Direction side) {
        return getOutputVoltage() > 0 && getOutputAmperage() > 0;
    }

    @Override
    public long changeEnergy(long differenceAmount) {
        long oldEnergy = energyStored;
        long newEnergy = Math.min(getEnergyCapacity(), Math.max(0, oldEnergy + differenceAmount));
        energyStored = newEnergy;
        return newEnergy - oldEnergy;
    }

    @Override
    public long getEnergyStored() {
        return energyStored;
    }

    @Override
    public long getEnergyCapacity() {
        return tier().voltage() * tier().maxAmperage() * GTOverheadConfig.bufferTicks();
    }

    @Override
    public long getOutputAmperage() {
        return tier().maxAmperage();
    }

    @Override
    public long getOutputVoltage() {
        return tier().voltage();
    }

    @Override
    public long getInputAmperage() {
        return tier().maxAmperage();
    }

    @Override
    public long getInputVoltage() {
        return tier().voltage();
    }

    public void setEnergyStored(long energy) {
        this.energyStored = Math.min(getEnergyCapacity(), Math.max(0, energy));
    }
}
