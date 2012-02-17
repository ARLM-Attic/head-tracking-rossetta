
using System;
using System.Collections.Generic;
using System.Text;
using System.Drawing;

namespace OpenHeadTrack
{
    class GlyphEnumerator : IEnumerator<Glyph>
    {
        #region Fields

        string text;
        Font font;

        IEnumerator<char> implementation;

        #endregion

        #region Constructors

        public GlyphEnumerator(string text, Font font)
        {
            if (text == null)
                throw new ArgumentNullException("text");

            if (font == null)
                throw new ArgumentNullException("font");

            this.text = text;
            this.font = font;

            implementation = text.GetEnumerator();
        }

        #endregion

        #region IEnumerator<Glyph> Members

        public Glyph Current
        {
            get { return new Glyph(implementation.Current, font); }
        }

        #endregion

        #region IDisposable Members

        public void Dispose()
        {
            implementation.Dispose();
        }

        #endregion

        #region IEnumerator Members

        object System.Collections.IEnumerator.Current
        {
            get { return new Glyph(implementation.Current, font); }
        }

        public bool MoveNext()
        {
            bool status;
            do
            {
                status = implementation.MoveNext();
            } while (status && (implementation.Current == '\n' || implementation.Current == '\r'));

            return status;
        }

        public void Reset()
        {
            implementation.Reset();
        }

        #endregion
    }
}
