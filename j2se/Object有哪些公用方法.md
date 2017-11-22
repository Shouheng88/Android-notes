## Object有哪些公用方法

答案 7 个：getClass() hashCode() equals() toString() notify() notifyAll() wait()

    public final native Class<?> getClass();

    public native int hashCode();

    public boolean equals(Object obj) {
        return (this == obj);
    }

    public String toString() {
        return getClass().getName() + "@" + Integer.toHexString(hashCode());
    }

    public final native void notify();

    public final native void notifyAll();

    public final native void wait(long timeout) throws InterruptedException;

    public final void wait(long timeout, int nanos) throws InterruptedException {/..../}

    public final void wait() throws InterruptedException {
        wait(0);
    }
