# /// script
# requires-python = ">=3.11"
# dependencies = ["segno"]
# ///
# The end card's QR code: the Google Play listing, in the app's ink. uv run qr.py -> assets/qr.svg
import segno
segno.make('https://play.google.com/store/apps/details?id=io.github.alpharomercoma.openweights', error='m').save(
    __file__.rsplit('/', 1)[0] + '/assets/qr.svg', scale=10, border=0, dark='#052B42', light=None)
print('wrote assets/qr.svg')
