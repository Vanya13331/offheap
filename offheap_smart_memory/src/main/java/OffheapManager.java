import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class OffheapManager {

    private static Unsafe unsafe;
    private static long offset;
    private static OffheapManager manager = new OffheapManager();
    private static ObjectTableItem[] tableItems;
    private Object obj;

    static {
        try {
            if (unsafe == null) {
                Field field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                unsafe = (Unsafe) field.get(null);
                offset = unsafe.objectFieldOffset(OffheapManager.class.getDeclaredField("obj"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Не удалось инстанцировать объект класса Unsafe", e);
        }
    }

    public static long getObjectAdress(Object o) {
        manager.obj = o;
        return unsafe.getLong(manager, offset);
    }

    public static Object getObjectFromAdress(long address) {
        unsafe.putLong(manager, offset, address);
        return manager.obj;
    }

    public boolean put(long key, byte[] value, long keyAddr) {
        int length = value.length;
        try {
            unsafe.putLong(keyAddr, key);
            unsafe.putInt(keyAddr + 2052, length);
            unsafe.copyMemory(value, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, 1L, length);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void put(Object o, long address) throws Exception {
        Class clazz = o.getClass();
        do {
            for (Field f : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    long offset = unsafe.objectFieldOffset(f);
                    if (f.getType() == long.class) {
                        unsafe.putLong(address + offset, unsafe.getLong(o, offset));
                    } else if (f.getType() == int.class) {
                        unsafe.putInt(address + offset, unsafe.getInt(o, offset));
                    } else {
                        throw new UnsupportedOperationException();
                    }
                }
            }
        } while ((clazz = clazz.getSuperclass()) != null);
    }

    public static Object get(Class clazz, long address, long keyAddr) {
        int offset = unsafe.getInt(keyAddr + 2048);
        int length = unsafe.getInt(keyAddr + 2052);
        byte[] result = new byte[length];
        unsafe.copyMemory(null, offset, result, Unsafe.ARRAY_BYTE_BASE_OFFSET, length);
        return result;
    }

    public static Object get(Class clazz, long address) throws Exception {
        Object instance = unsafe.allocateInstance(clazz);
        do {
            for (Field f : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    long offset = unsafe.objectFieldOffset(f);

                    if (f.getType() == long.class) {
                        unsafe.putLong(instance, offset, unsafe.getLong(address + offset));
                    } else if (f.getType() == int.class) {
                        unsafe.putLong(instance, offset, unsafe.getInt(address + offset));
                    } else if (f.getType() == char.class) {
                        unsafe.putChar(instance, offset, unsafe.getChar(address + offset));
                    } else {
                        throw new UnsupportedOperationException();
                    }
                }
            }
        } while ((clazz = clazz.getSuperclass()) != null);
        return instance;
    }

    public static long sizeOf(Object object) {
        return new ObjectSizeAgent().getObjSize(object);
    }

    public static byte[] toByteArray(Object obj) {
        int len = (int) sizeOf(obj);
        byte[] bytes = new byte[len];
        unsafe.copyMemory(obj, 0, bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET, bytes.length);
        return bytes;
    }

    public static long sizeOf(Class clazz) {
        long maxSize = headerSize(clazz);

        while (clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if ((f.getModifiers() & Modifier.STATIC) == 0) {
                    long offset = unsafe.objectFieldOffset(f);
                    if (offset > maxSize) {
                        maxSize = offset + 1;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }

        return makePaddingTo8(maxSize);
    }

    private static long makePaddingTo8(final long number) {
        return ((number + 7) / 8) * 8;
    }

    public static long headerSize(Class clazz) {
        long len = 12;
        if (clazz.isArray()) {
            len += 4;
        }
        return len;
    }


    public class ObjectTableItem {
        final long start;
        int end;
        int count;

        ObjectTableItem(long start, int size) {
            this.start = start;
            init(start, size);
        }

        private void init(long start, int size) {
            int maxTail = 4096;
            long prevKey = 0;
            long pos = start;

            for (long keysEnd = start + 2048; pos < keysEnd; pos += 8) {
                long key = unsafe.getLong(pos);
                if (key <= prevKey) {
                    break;
                }

                int offset = unsafe.getInt(pos + 2048);
                int length = unsafe.getInt(pos + 2052);
                int newTail = offset + length;
                if (offset < 4096 || length < 0 || newTail > size) {
                    break;
                }

                if (newTail > maxTail) {
                    maxTail = newTail;
                }
                prevKey = key;
            }

            this.end = maxTail;
            this.count = (int) (pos - start) >>> 3;
        }
    }
}
