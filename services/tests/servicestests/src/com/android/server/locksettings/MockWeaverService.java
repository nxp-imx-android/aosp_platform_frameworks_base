package com.android.server.locksettings;

import android.hardware.weaver.V1_0.IWeaver;
import android.hardware.weaver.V1_0.WeaverConfig;
import android.hardware.weaver.V1_0.WeaverReadResponse;
import android.hardware.weaver.V1_0.WeaverStatus;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.Arrays;

public class MockWeaverService extends IWeaver.Stub {

    private static final int MAX_SLOTS = 8;
    private static final int KEY_LENGTH = 256 / 8;
    private static final int VALUE_LENGTH = 256 / 8;

    private static class WeaverSlot {
        public ArrayList<Byte> key;
        public ArrayList<Byte> value;
        public int failureCounter;
    }

    private final WeaverSlot[] mSlots = new WeaverSlot[MAX_SLOTS];

    public MockWeaverService() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            mSlots[i] = new WeaverSlot();
        }
    }

    @Override
    public void getConfig(getConfigCallback cb) throws RemoteException {
        WeaverConfig config = new WeaverConfig();
        config.keySize = KEY_LENGTH;
        config.valueSize = VALUE_LENGTH;
        config.slots = MAX_SLOTS;
        cb.onValues(WeaverStatus.OK, config);
    }

    @Override
    public int write(int slotId, ArrayList<Byte> key, ArrayList<Byte> value)
            throws RemoteException {
        if (slotId < 0 || slotId >= MAX_SLOTS) {
            throw new RuntimeException("Invalid slot id");
        }
        WeaverSlot slot = mSlots[slotId];
        slot.key = (ArrayList<Byte>) key.clone();
        slot.value = (ArrayList<Byte>) value.clone();
        slot.failureCounter = 0;
        return WeaverStatus.OK;
    }

    @Override
    public void read(int slotId, ArrayList<Byte> key, readCallback cb) throws RemoteException {
        if (slotId < 0 || slotId >= MAX_SLOTS) {
            throw new RuntimeException("Invalid slot id");
        }

        WeaverReadResponse response = new WeaverReadResponse();
        WeaverSlot slot = mSlots[slotId];
        if (key.equals(slot.key)) {
            response.value.addAll(slot.value);
            cb.onValues(WeaverStatus.OK, response);
            slot.failureCounter = 0;
        } else {
            cb.onValues(WeaverStatus.FAILED, response);
            slot.failureCounter++;
        }
    }

    public int getSumOfFailureCounters() {
        return Arrays.stream(mSlots).mapToInt(slot -> slot.failureCounter).sum();
    }
}
