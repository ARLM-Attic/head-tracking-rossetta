using System;
using System.Collections.Generic;
using System.Text;

namespace OpenHeadTrack
{
    /// <summary>
    /// Represents an item that can be packed with the TexturePacker.
    /// </summary>
    /// <typeparam name="T">The type of the packable item.</typeparam>
    interface IPackable<T> : IEquatable<T>
    {
        int Width { get; }
        int Height { get; }
    }
}