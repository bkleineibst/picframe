package picframe.at.picframe.helper.viewpager;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.media.ExifInterface;
import android.util.Log;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.view.Display;
import android.view.WindowManager;

/**
 * Created by ClemensH on 06.04.2015.
 */
public class EXIF_helper {
    private ExifInterface reader = null;

    public static Bitmap decodeFile(String filePath, Context myContext) {

        // Decode image size
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, o);

        // The new size we want to scale to ( max display resolution)
        WindowManager wm = (WindowManager) myContext.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();
        int REQUIRED_SIZE = 2048;
        REQUIRED_SIZE = width>height? width : height;

        // Find the correct scale value. It should be the power of 2.
        int width_tmp = o.outWidth;
        int height_tmp = o.outHeight;
        int scale = 1;
        while (true) {
            if (width_tmp <= REQUIRED_SIZE && height_tmp <= REQUIRED_SIZE)
                break;
            width_tmp /= 2;
            height_tmp /= 2;
            scale *= 2;
        }
        //System.out.println("Picture width: " + width_tmp + " --- Pic height: " + height_tmp + "\nScaleFactor: " +scale);

        // Decode with correct scale (inSampleSize)
        o = new BitmapFactory.Options();
        o.inSampleSize = scale;
        Bitmap b1 = BitmapFactory.decodeFile(filePath, o);
        // Rotate scaled image according to EXIF Information stored in the file
        Bitmap b = EXIF_helper.rotateBitmap(filePath, b1);

        return b;
        // image.setImageBitmap(bitmap);
    }

// @see http://sylvana.net/jpegcrop/exif_orientation.html
    public static Bitmap rotateBitmap(String src, Bitmap bitmap) {
        try {
            int orientation = getExifOrientation(src);
            if (orientation == 1) {
                return bitmap;
            }

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.setScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.setRotate(180);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.setRotate(180);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix.setRotate(90);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.setRotate(90);
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix.setRotate(-90);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.setRotate(-90);
                    break;
                default:
                    return bitmap;
            }

            try {
                Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                bitmap.recycle();
                return oriented;
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
                return bitmap;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bitmap;
    }

    private static int getExifOrientation(String src) throws IOException {
        int orientation = ExifInterface.ORIENTATION_NORMAL;

        try {
          ExifInterface exif = new ExifInterface(src);
          orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        return orientation;
    }

    /**
     * Extracts location information from image EXIF data and returns a human-readable location string.
     * @param filePath Path to the image file
     * @param format Location format: 0 = city and country, 1 = city only
     * @return Location string or null if no location data available
     */
    public static String getLocationFromImage(String filePath, int format) {
        try {
            ExifInterface exif = new ExifInterface(filePath);
            float[] latLong = new float[2];
            boolean hasLatLong = exif.getLatLong(latLong);
            if (hasLatLong) {
                float latitude = latLong[0];
                float longitude = latLong[1];
                
                // Try to get a human-readable location name using reverse geocoding
                try {
                    Context context = picframe.at.picframe.activities.MainActivity.getContext();
                    if (context != null && Geocoder.isPresent()) {
                        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                        List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                        if (addresses != null && !addresses.isEmpty()) {
                            Address address = addresses.get(0);
                            StringBuilder locationBuilder = new StringBuilder();
                            
                            // Try to build a meaningful location string
                            String locality = address.getLocality();
                            String subLocality = address.getSubLocality();
                            String adminArea = address.getAdminArea();
                            String country = address.getCountryName();
                            
                            if (locality != null) {
                                locationBuilder.append(locality);
                            } else if (subLocality != null) {
                                locationBuilder.append(subLocality);
                            } else if (adminArea != null) {
                                locationBuilder.append(adminArea);
                            }
                            
                            // Only add country if format is 0 (city and country)
                            if (format == 0 && country != null && locationBuilder.length() > 0) {
                                locationBuilder.append(", ").append(country);
                            } else if (format == 0 && country != null && locationBuilder.length() == 0) {
                                locationBuilder.append(country);
                            }
                            
                            if (locationBuilder.length() > 0) {
                                String result = locationBuilder.toString();
                                return result;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("EXIF_helper", "Geocoding failed", e);
                    e.printStackTrace();
                }
                
                // Fallback to coordinates if geocoding fails
                String coords = String.format(Locale.US, "%.4f, %.4f", latitude, longitude);
                return coords;
            }
        } catch (IOException e) {
            Log.e("EXIF_helper", "IOException reading EXIF", e);
            e.printStackTrace();
        } catch (SecurityException e) {
            Log.e("EXIF_helper", "SecurityException reading EXIF", e);
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            Log.e("EXIF_helper", "IllegalArgumentException reading EXIF", e);
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Extracts the date/time when the photo was taken from EXIF data.
     * @param filePath Path to the image file
     * @return Date object representing when the photo was taken, or null if not available
     */
    public static java.util.Date getDateFromImage(String filePath) {
        try {
            ExifInterface exif = new ExifInterface(filePath);
            
            // Try DATETIME_ORIGINAL first (when photo was actually taken)
            String dateString = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            if (dateString == null) {
                // Fall back to DATETIME
                dateString = exif.getAttribute(ExifInterface.TAG_DATETIME);
            }
            if (dateString == null) {
                // Fall back to DATETIME_DIGITIZED
                dateString = exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED);
            }
            
            if (dateString != null) {
                // EXIF date format is "yyyy:MM:dd HH:mm:ss"
                java.text.SimpleDateFormat exifFormat = new java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
                try {
                    java.util.Date date = exifFormat.parse(dateString);
                    return date;
                } catch (java.text.ParseException e) {
                    Log.e("EXIF_helper", "Failed to parse EXIF date: " + dateString);
                }
            }
        } catch (IOException e) {
            Log.e("EXIF_helper", "IOException reading EXIF date", e);
        } catch (SecurityException e) {
            Log.e("EXIF_helper", "SecurityException reading EXIF date", e);
        } catch (IllegalArgumentException e) {
            Log.e("EXIF_helper", "IllegalArgumentException reading EXIF date", e);
        }
        
        // Try to extract date from filename as fallback (format: yyyyMMdd_HHmmss)
        try {
            String filename = new java.io.File(filePath).getName();
            // Match patterns like 20230307_094142.jpg
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{8})_(\\d{6})");
            java.util.regex.Matcher matcher = pattern.matcher(filename);
            if (matcher.find()) {
                String dateStr = matcher.group(1) + matcher.group(2);
                java.text.SimpleDateFormat filenameFormat = new java.text.SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
                java.util.Date date = filenameFormat.parse(dateStr);
                return date;
            }
        } catch (Exception e) {
            Log.e("EXIF_helper", "Failed to parse date from filename", e);
        }
        
        return null;
    }

    /*
    	// Change color scale of bitmap to grayscale
	public Bitmap ConvertToGrayscale(Bitmap sampleBitmap) {
		final ColorMatrix gsMatrix = new ColorMatrix();
		gsMatrix.setSaturation(0);
		final ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(
				gsMatrix);
		sampleBitmap = sampleBitmap.copy(Bitmap.Config.ARGB_8888, true);
		final Paint paint = new Paint();
		paint.setColorFilter(colorFilter);
		final Canvas myCanvas = new Canvas(sampleBitmap);
		myCanvas.drawBitmap(sampleBitmap, 0, 0, paint);
		Log.i(LOG_PROV, LOG_NAME
				+ "Changed Bitmap to grayscale in ConvertToGrayScale");
		return sampleBitmap;
	}

	// Change color scale of bitmap to sepia
	public Bitmap ConvertToSepia(Bitmap sampleBitmap) {
		final ColorMatrix sepiaMatrix = new ColorMatrix();
		final float[] sepMat = { 0.3930000066757202f, 0.7689999938011169f,
				0.1889999955892563f, 0, 0, 0.3490000069141388f,
				0.6859999895095825f, 0.1679999977350235f, 0, 0,
				0.2720000147819519f, 0.5339999794960022f, 0.1309999972581863f,
				0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1 };
		sepiaMatrix.set(sepMat);
		final ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(
				sepiaMatrix);
		sampleBitmap = sampleBitmap.copy(Bitmap.Config.ARGB_8888, true);
		final Paint paint = new Paint();
		paint.setColorFilter(colorFilter);
		final Canvas myCanvas = new Canvas(sampleBitmap);
		myCanvas.drawBitmap(sampleBitmap, 0, 0, paint);
		Log.i(LOG_PROV, LOG_NAME + "Changed Bitmap to sepia in ConvertToSepia");
		return sampleBitmap;
	}
    * */
}
