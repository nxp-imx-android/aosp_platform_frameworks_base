/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.ravenwood.RavenwoodRule;
import android.platform.test.ravenwood.RavenwoodRule;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.IdentityHashMap;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class ParcelTest {
    @Rule public final RavenwoodRule mRavenwood = new RavenwoodRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int WORK_SOURCE_1 = 1000;
    private static final int WORK_SOURCE_2 = 1002;
    private static final String INTERFACE_TOKEN_1 = "IBinder interface token";
    private static final String INTERFACE_TOKEN_2 = "Another IBinder interface token";

    @Test
    public void testIsForRpc() {
        Parcel p = Parcel.obtain();
        assertEquals(false, p.isForRpc());
        p.recycle();
    }

    @Test
    public void testCallingWorkSourceUidAfterWrite() {
        Parcel p = Parcel.obtain();
        // Method does not throw if replaceCallingWorkSourceUid is called before requests headers
        // are added.
        assertEquals(false, p.replaceCallingWorkSourceUid(WORK_SOURCE_1));
        assertEquals(Binder.UNSET_WORKSOURCE, p.readCallingWorkSourceUid());

        // WorkSource can be updated.
        p.writeInterfaceToken(INTERFACE_TOKEN_1);
        assertEquals(true, p.replaceCallingWorkSourceUid(WORK_SOURCE_2));
        assertEquals(WORK_SOURCE_2, p.readCallingWorkSourceUid());

        // WorkSource can be updated to unset value.
        assertEquals(true, p.replaceCallingWorkSourceUid(Binder.UNSET_WORKSOURCE));
        assertEquals(Binder.UNSET_WORKSOURCE, p.readCallingWorkSourceUid());

        p.recycle();
    }

    @Test
    public void testCallingWorkSourceUidAfterEnforce() {
        Parcel p = Parcel.obtain();
        p.writeInterfaceToken(INTERFACE_TOKEN_1);
        assertEquals(true, p.replaceCallingWorkSourceUid(WORK_SOURCE_1));
        p.setDataPosition(0);

        p.enforceInterface(INTERFACE_TOKEN_1);
        assertEquals(WORK_SOURCE_1, p.readCallingWorkSourceUid());

        // WorkSource can be updated.
        assertEquals(true, p.replaceCallingWorkSourceUid(WORK_SOURCE_2));
        assertEquals(WORK_SOURCE_2, p.readCallingWorkSourceUid());

        p.recycle();
    }

    @Test
    public void testParcelWithMultipleHeaders() {
        Parcel p = Parcel.obtain();
        Binder.setCallingWorkSourceUid(WORK_SOURCE_1);
        p.writeInterfaceToken(INTERFACE_TOKEN_1);
        Binder.setCallingWorkSourceUid(WORK_SOURCE_2);
        p.writeInterfaceToken(INTERFACE_TOKEN_2);
        p.setDataPosition(0);

        // WorkSource is from the first header.
        p.enforceInterface(INTERFACE_TOKEN_1);
        assertEquals(WORK_SOURCE_1, p.readCallingWorkSourceUid());
        p.enforceInterface(INTERFACE_TOKEN_2);
        assertEquals(WORK_SOURCE_1, p.readCallingWorkSourceUid());

        p.recycle();
    }

    /** Verify that writing/reading UTF-8 and UTF-16 strings works well. */
    @Test
    public void testStrings() {
        final String[] strings = {
            null,
            "",
            "abc\0def",
            "com.example.typical_package_name",
            "從不喜歡孤單一個 - 蘇永康／吳雨霏",
            "example"
        };

        final Parcel p = Parcel.obtain();
        for (String string : strings) {
            p.writeString8(string);
            p.writeString16(string);
        }

        p.setDataPosition(0);
        for (String string : strings) {
            assertEquals(string, p.readString8());
            assertEquals(string, p.readString16());
        }
    }

    @Test
    public void testCompareDataInRange_whenSameData() {
        Parcel pA = Parcel.obtain();
        int iA = pA.dataPosition();
        pA.writeInt(13);
        pA.writeString("Tiramisu");
        int length = pA.dataPosition() - iA;
        Parcel pB = Parcel.obtain();
        pB.writeString("Prefix");
        int iB = pB.dataPosition();
        pB.writeInt(13);
        pB.writeString("Tiramisu");

        assertTrue(Parcel.compareData(pA, iA, pB, iB, length));
    }

    @Test
    public void testCompareDataInRange_whenSameDataWithBinder() {
        Binder binder = new Binder();
        Parcel pA = Parcel.obtain();
        int iA = pA.dataPosition();
        pA.writeInt(13);
        pA.writeStrongBinder(binder);
        pA.writeString("Tiramisu");
        int length = pA.dataPosition() - iA;
        Parcel pB = Parcel.obtain();
        pB.writeString("Prefix");
        int iB = pB.dataPosition();
        pB.writeInt(13);
        pB.writeStrongBinder(binder);
        pB.writeString("Tiramisu");

        assertTrue(Parcel.compareData(pA, iA, pB, iB, length));
    }

    @Test
    public void testCompareDataInRange_whenDifferentData() {
        Parcel pA = Parcel.obtain();
        int iA = pA.dataPosition();
        pA.writeInt(13);
        pA.writeString("Tiramisu");
        int length = pA.dataPosition() - iA;
        Parcel pB = Parcel.obtain();
        int iB = pB.dataPosition();
        pB.writeString("Prefix");
        pB.writeInt(13);
        pB.writeString("Tiramisu");

        assertFalse(Parcel.compareData(pA, iA, pB, iB, length));
    }

    @Test
    public void testCompareDataInRange_whenLimitOutOfBounds_throws() {
        Parcel pA = Parcel.obtain();
        int iA = pA.dataPosition();
        pA.writeInt(12);
        pA.writeString("Tiramisu");
        int length = pA.dataPosition() - iA;
        Parcel pB = Parcel.obtain();
        pB.writeString("Prefix");
        int iB = pB.dataPosition();
        pB.writeInt(13);
        pB.writeString("Tiramisu");
        pB.writeInt(-1);

        assertThrows(
                IllegalArgumentException.class,
                () -> Parcel.compareData(pA, iA + length, pB, iB, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> Parcel.compareData(pA, iA, pB, pB.dataSize(), 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> Parcel.compareData(pA, iA, pB, iB, length + 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> Parcel.compareData(pA, iA + length + 1, pB, iB, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> Parcel.compareData(pA, iA, pB, iB + pB.dataSize() + 1, 0));
    }

    @Test
    public void testCompareDataInRange_whenLengthZero() {
        Parcel pA = Parcel.obtain();
        int iA = pA.dataPosition();
        pA.writeInt(12);
        pA.writeString("Tiramisu");
        int length = pA.dataPosition() - iA;
        Parcel pB = Parcel.obtain();
        pB.writeString("Prefix");
        int iB = pB.dataPosition();
        pB.writeInt(13);
        pB.writeString("Tiramisu");

        assertTrue(Parcel.compareData(pA, 0, pB, iB, 0));
        assertTrue(Parcel.compareData(pA, iA + length, pB, iB, 0));
        assertTrue(Parcel.compareData(pA, iA, pB, pB.dataSize(), 0));
    }

    @Test
    public void testCompareDataInRange_whenNegativeLength_throws() {
        Parcel pA = Parcel.obtain();
        int iA = pA.dataPosition();
        pA.writeInt(12);
        pA.writeString("Tiramisu");
        Parcel pB = Parcel.obtain();
        pB.writeString("Prefix");
        int iB = pB.dataPosition();
        pB.writeInt(13);
        pB.writeString("Tiramisu");

        assertThrows(IllegalArgumentException.class, () -> Parcel.compareData(pA, iA, pB, iB, -1));
    }

    @Test
    public void testCompareDataInRange_whenNegativeOffset_throws() {
        Parcel pA = Parcel.obtain();
        int iA = pA.dataPosition();
        pA.writeInt(12);
        pA.writeString("Tiramisu");
        Parcel pB = Parcel.obtain();
        pB.writeString("Prefix");
        int iB = pB.dataPosition();
        pB.writeInt(13);
        pB.writeString("Tiramisu");

        assertThrows(IllegalArgumentException.class, () -> Parcel.compareData(pA, -1, pB, iB, 0));
        assertThrows(IllegalArgumentException.class, () -> Parcel.compareData(pA, 0, pB, -1, 0));
    }

    /***
     * Tests for b/205282403
     * This test checks if allocations made over limit of 1MB for primitive types
     * and 1M length for complex objects are not allowed.
     */
    @Test
    public void testAllocationsOverLimit_whenOverLimit_throws() {
        Binder.setIsDirectlyHandlingTransactionOverride(true);
        Parcel p = Parcel.obtain();
        p.setDataPosition(0);
        p.writeInt(Integer.MAX_VALUE);

        p.setDataPosition(0);
        assertThrows(BadParcelableException.class, () -> p.createBooleanArray());

        p.setDataPosition(0);
        assertThrows(BadParcelableException.class, () -> p.createCharArray());

        p.setDataPosition(0);
        assertThrows(BadParcelableException.class, () -> p.createIntArray());

        p.setDataPosition(0);
        assertThrows(BadParcelableException.class, () -> p.createLongArray());

        p.setDataPosition(0);
        assertThrows(BadParcelableException.class, () -> p.createBinderArray());

        int[] dimensions = new int[] {Integer.MAX_VALUE, 100, 100};
        p.setDataPosition(0);
        assertThrows(
                BadParcelableException.class,
                () -> p.createFixedArray(int[][][].class, dimensions));

        p.setDataPosition(0);
        assertThrows(
                BadParcelableException.class,
                () -> p.createFixedArray(String[][][].class, dimensions));

        p.setDataPosition(0);
        assertThrows(
                BadParcelableException.class,
                () -> p.createFixedArray(IBinder[][][].class, dimensions));

        p.recycle();
        Binder.setIsDirectlyHandlingTransactionOverride(false);
    }

    /***
     * Tests for b/205282403
     * This test checks if allocations made under limit of 1MB for primitive types
     * and 1M length for complex objects are allowed.
     */
    @Test
    public void testAllocations_whenWithinLimit() {
        Binder.setIsDirectlyHandlingTransactionOverride(true);
        Parcel p = Parcel.obtain();
        p.setDataPosition(0);
        p.writeInt(100000);

        p.setDataPosition(0);
        p.createByteArray();

        p.setDataPosition(0);
        p.createCharArray();

        p.setDataPosition(0);
        p.createIntArray();

        p.setDataPosition(0);
        p.createLongArray();

        p.setDataPosition(0);
        p.createBinderArray();

        int[] dimensions = new int[] {100, 100, 100};

        p.setDataPosition(0);
        int[][][] data = new int[100][100][100];
        p.writeFixedArray(data, 0, dimensions);
        p.setDataPosition(0);
        p.createFixedArray(int[][][].class, dimensions);

        p.setDataPosition(0);
        IBinder[][][] parcelables = new IBinder[100][100][100];
        p.writeFixedArray(parcelables, 0, dimensions);
        p.setDataPosition(0);
        p.createFixedArray(IBinder[][][].class, dimensions);

        p.recycle();
        Binder.setIsDirectlyHandlingTransactionOverride(false);
    }

    @Test
    public void testClassCookies() {
        Parcel p = Parcel.obtain();
        assertThat(p.hasClassCookie(ParcelTest.class)).isFalse();

        p.setClassCookie(ParcelTest.class, "string_cookie");
        assertThat(p.hasClassCookie(ParcelTest.class)).isTrue();
        assertThat(p.getClassCookie(ParcelTest.class)).isEqualTo("string_cookie");

        p.removeClassCookie(ParcelTest.class, "string_cookie");
        assertThat(p.hasClassCookie(ParcelTest.class)).isFalse();
        assertThat(p.getClassCookie(ParcelTest.class)).isEqualTo(null);

        p.setClassCookie(ParcelTest.class, "to_be_discarded_cookie");
        p.recycle();

        // cannot access Parcel after it's recycled!
        // this test is equivalent to checking hasClassCookie false
        // after obtaining above
        // assertThat(p.getClassCookie(ParcelTest.class)).isNull();
    }

    @Test
    public void testClassCookies_removeUnexpected() {
        Parcel p = Parcel.obtain();

        assertLogsWtf(() -> p.removeClassCookie(ParcelTest.class, "not_present"));

        p.setClassCookie(ParcelTest.class, "value");

        assertLogsWtf(() -> p.removeClassCookie(ParcelTest.class, "different"));
        assertThat(p.getClassCookie(ParcelTest.class)).isNull(); // still removed

        p.recycle();
    }

    private static void assertLogsWtf(Runnable test) {
        ArrayList<Log.TerribleFailure> wtfs = new ArrayList<>();
        Log.TerribleFailureHandler oldHandler =
                Log.setWtfHandler((tag, what, system) -> wtfs.add(what));
        try {
            test.run();
        } finally {
            Log.setWtfHandler(oldHandler);
        }
        assertThat(wtfs).hasSize(1);
    }

    @Test
    public void testHasBinders_AfterWritingBinderToParcel() {
        Binder binder = new Binder();
        Parcel pA = Parcel.obtain();
        int iA = pA.dataPosition();
        pA.writeInt(13);
        assertFalse(pA.hasBinders());
        pA.writeStrongBinder(binder);
        assertTrue(pA.hasBinders());
    }

    @Test
    public void testHasBindersInRange_AfterWritingBinderToParcel() {
        Binder binder = new Binder();
        Parcel pA = Parcel.obtain();
        pA.writeInt(13);

        int binderStartPos = pA.dataPosition();
        pA.writeStrongBinder(binder);
        int binderEndPos = pA.dataPosition();
        assertTrue(pA.hasBinders(binderStartPos, binderEndPos - binderStartPos));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    @DisabledOnRavenwood // Requires JniStringCache which is not available on host builds
    public void testSharedStringCache_String16() {
        final String s1 = "hello";
        final String s2 = new String("hello");
        final String s3 = "world";

        // Write and read s1 from parcel 1
        Parcel p1 = Parcel.obtain();
        p1.writeString(s1);
        p1.setDataPosition(0);
        String r1 = p1.readString();
        p1.recycle();

        // Write and read s2 from parcel 2
        Parcel p2 = Parcel.obtain();
        p2.writeString(s2);
        p2.setDataPosition(0);
        String r2 = p2.readString();
        p2.recycle();

        // Write and read s3 from parcel 3
        Parcel p3 = Parcel.obtain();
        p3.writeString(s3);
        p3.setDataPosition(0);
        String r3 = p3.readString();
        p3.recycle();

        assertEquals(s1, r1);
        assertEquals(s2, r2);
        assertEquals(s3, r3);

        // Check that strings with the same content are the same instance due to caching
        assertTrue("Expected r1 and r2 to be the same instance", r1 == r2);
        assertFalse("Expected r1 and r3 to be different instances", r1 == r3);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    @DisabledOnRavenwood // Requires JniStringCache which is not available on host builds
    public void testSharedStringCache_String8() {
        final String s1 = "hello";
        final String s2 = new String("hello");
        final String s3 = "world";

        // Write and read s1 from parcel 1
        Parcel p1 = Parcel.obtain();
        p1.writeString8(s1);
        p1.setDataPosition(0);
        String r1 = p1.readString8();
        p1.recycle();

        // Write and read s2 from parcel 2
        Parcel p2 = Parcel.obtain();
        p2.writeString8(s2);
        p2.setDataPosition(0);
        String r2 = p2.readString8();
        p2.recycle();

        // Write and read s3 from parcel 3
        Parcel p3 = Parcel.obtain();
        p3.writeString8(s3);
        p3.setDataPosition(0);
        String r3 = p3.readString8();
        p3.recycle();

        assertEquals(s1, r1);
        assertEquals(s2, r2);
        assertEquals(s3, r3);

        // Check that strings with the same content are the same instance due to caching
        assertTrue("Expected r1 and r2 to be the same instance", r1 == r2);
        assertFalse("Expected r1 and r3 to be different instances", r1 == r3);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    @DisabledOnRavenwood // Requires JniStringCache which is not available on host builds
    /** Stress test for Parcel and JniStringCache integration. */
    public void testSharedStringCache_stress() {
        // Provide enough unique character sequences for a meaningful stress test
        final String[] A_VISIT_FROM_ST_NICHOLAS =
                new String[] {
                    "'Twas the night before Christmas, when all through the house",
                    "Not a creature was stirring, not even a mouse;",
                    "The stockings were hung by the chimney with care,",
                    "In hopes that St. Nicholas soon would be there;",
                    "The children were nestled all snug in their beds,",
                    "While visions of sugar-plums danced in their heads;",
                    "And mamma in her ‘kerchief, and I in my cap,",
                    "Had just settled our brains for a long winter’s nap,",
                    "When out on the lawn there arose such a clatter,",
                    "I sprang from the bed to see what was the matter.",
                    "Away to the window I flew like a flash,",
                    "Tore open the shutters and threw up the sash.",
                    "The moon on the breast of the new-fallen snow",
                    "Gave the lustre of mid-day to objects below,",
                    "When, what to my wondering eyes should appear,",
                    "But a miniature sleigh, and eight tiny reindeer,",
                    "With a little old driver, so lively and quick,",
                    "I knew in a moment it must be St. Nick.",
                    "More rapid than eagles his coursers they came,",
                    "And he whistled, and shouted, and called them by name;",
                    "\"Now, Dasher! now, Dancer! now, Prancer and Vixen!",
                    "On, Comet! on Cupid! on, Donder and Blitzen!",
                    "To the top of the porch! to the top of the wall!",
                    "Now dash away! dash away! dash away all!\"",
                    "As dry leaves that before the wild hurricane fly,",
                    "When they meet with an obstacle, mount to the sky,",
                    "So up to the house-top the coursers they flew,",
                    "With the sleigh full of toys, and St. Nicholas too.",
                    "And then, in a twinkling, I heard on the roof",
                    "The prancing and pawing of each little hoof.",
                    "As I drew in my head, and was turning around,",
                    "Down the chimney St. Nicholas came with a bound.",
                    "He was dressed all in fur, from his head to his foot,",
                    "And his clothes were all tarnished with ashes and soot;",
                    "A bundle of toys he had flung on his back,",
                    "And he looked like a peddler just opening his pack.",
                    "His eyes—how they twinkled! his dimples how merry!",
                    "His cheeks were like roses, his nose like a cherry!",
                    "His drolly little mouth was drawn up like a bow,",
                    "And the beard of his chin was as white as the snow;",
                    "The stump of a pipe he held tight in his teeth,",
                    "And the smoke it encircled his head like a wreath;",
                    "He had a broad face and a little round belly,",
                    "That shook, when he laughed, like a bowlful of jelly.",
                    "He was chubby and plump, a right jolly old elf,",
                    "And I laughed when I saw him, in spite of myself;",
                    "A wink of his eye and a twist of his head,",
                    "Soon gave me to know I had nothing to dread;",
                    "He spoke not a word, but went straight to his work,",
                    "And filled all the stockings; then turned with a jerk,",
                    "And laying his finger aside of his nose,",
                    "And giving a nod, up the chimney he rose;",
                    "He sprang to his sleigh, to his team gave a whistle,",
                    "And away they all flew like the down of a thistle.",
                    "But I heard him exclaim, ere he drove out of sight,",
                    "\"Happy Christmas to all, and to all a good-night.\""
                };

        Parcel p = Parcel.obtain();
        for (String s : A_VISIT_FROM_ST_NICHOLAS) {
            p.writeString(s);
            p.writeString(s);
            p.writeString(s);
        }

        final String[] readBack = new String[A_VISIT_FROM_ST_NICHOLAS.length * 3];
        p.setDataPosition(0);
        for (int readIndex = 0, writeIndex = 0;
                writeIndex < A_VISIT_FROM_ST_NICHOLAS.length;
                writeIndex++) {
            readBack[readIndex++] = p.readString();
            readBack[readIndex++] = p.readString();
            readBack[readIndex++] = p.readString();
        }
        p.recycle();

        IdentityHashMap<String, String> instancesSeen = new IdentityHashMap<>();
        for (int readIndex = 0, writeIndex = 0;
                writeIndex < A_VISIT_FROM_ST_NICHOLAS.length;
                writeIndex++) {
            String readString1 = readBack[readIndex++];
            assertEquals(
                    "Expected content equality, at writeIndex = " + writeIndex,
                    readString1,
                    A_VISIT_FROM_ST_NICHOLAS[writeIndex]);
            assertEquals(
                    "Expected previously unseen instance, at writeIndex = " + writeIndex,
                    null,
                    instancesSeen.put(readString1, readString1));

            String readString2 = readBack[readIndex++];
            assertEquals(
                    "Expected content equality, at writeIndex = " + writeIndex,
                    readString1,
                    readString2);
            assertTrue(
                    "Expected instance equality, at writeIndex = " + writeIndex,
                    readString1 == readString2);
            assertNotEquals(
                    "Expected previously seen instance, at writeIndex = " + writeIndex,
                    null,
                    instancesSeen.put(readString2, readString2));

            String readString3 = readBack[readIndex++];
            assertTrue(
                    "Expected instance equality, at writeIndex = " + writeIndex,
                    readString1 == readString3);
            assertEquals(
                    "Expected content equality, at writeIndex = " + writeIndex,
                    readString1,
                    readString3);
            assertNotEquals(
                    "Expected previously seen instance, at writeIndex = " + writeIndex,
                    null,
                    instancesSeen.put(readString3, readString3));
        }
    }
}
