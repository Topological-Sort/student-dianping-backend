package com.studp.utils;

public interface ILock {

    // timeoutSec: 持有锁持续时间
    boolean tryLock(long timeoutSec);

    void unlock();
}
