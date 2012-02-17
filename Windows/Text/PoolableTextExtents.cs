using System;
using System.Collections.Generic;
using System.Text;

namespace OpenHeadTrack.Text
{
    class ObjectPool<T> where T : IPoolable<T>, new()
    {
        Queue<T> pool = new Queue<T>();

        public ObjectPool()
        { }

        public T Acquire()
        {
            T item;

            if (pool.Count > 0)
            {
                item = pool.Dequeue();
                item.OnAcquire();
            }
            else
            {
                item = new T();
                item.Owner = this;
                item.OnAcquire();
            }

            return item;
        }

        public void Release(T item)
        {
            if (item == null)
                throw new ArgumentNullException("item");

            item.OnRelease();
            pool.Enqueue(item);
        }
    }

    interface IPoolable : IDisposable
    {
        void OnAcquire();
        void OnRelease();
    }

    interface IPoolable<T> : IPoolable where T : IPoolable<T>, new()
    {
        ObjectPool<T> Owner { get; set; }
    }

    class PoolableTextExtents : TextExtents, IPoolable<PoolableTextExtents>
    {
        ObjectPool<PoolableTextExtents> owner;

        #region Constructors

        public PoolableTextExtents()
        {
        }

        #endregion

        #region IPoolable<PoolableTextExtents> Members

        ObjectPool<PoolableTextExtents> IPoolable<PoolableTextExtents>.Owner
        {
            get { return owner; }
            set { owner = value; }
        }

        #endregion

        #region IPoolable Members

        void IPoolable.OnAcquire()
        {
            Clear();
        }

        void IPoolable.OnRelease()
        {
        }

        #endregion
    }
}