/*
 * Copyright (c) 2013, Expedia Affiliate Network
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that redistributions of source code
 * retain the above copyright notice, these conditions, and the following
 * disclaimer. 
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those
 * of the authors and should not be interpreted as representing official policies, 
 * either expressed or implied, of the Expedia Affiliate Network or Expedia Inc.
 */

package com.ean.mobile.app;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.joda.time.LocalDate;

import android.app.Application;
import android.content.Context;
import android.net.http.HttpResponseCache;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.ean.mobile.Constants;
import com.ean.mobile.R;
import com.ean.mobile.hotel.Hotel;
import com.ean.mobile.hotel.HotelImageTuple;
import com.ean.mobile.hotel.HotelInformation;
import com.ean.mobile.hotel.HotelList;
import com.ean.mobile.hotel.HotelRoom;
import com.ean.mobile.hotel.Reservation;
import com.ean.mobile.hotel.RoomOccupancy;
import com.ean.mobile.request.CommonParameters;

/**
 * The foundation of the sample-app, this class is the main holder of state information for the app.
 */
public final class SampleApp extends Application {

    /**
     * The query used for the most recent hotel api search.
     */
    public static String searchQuery;

    /**
     * The number of adults intended for the room searched for with  {@link SampleApp#searchQuery}.
     */
    public static int numberOfAdults;

    /**
     * The number of children intended for the room searched for with {@link SampleApp#searchQuery}.
     */
    public static List<Integer> childAges;

    /**
     * The date intended for arrival at the destination searched for with {@link SampleApp#searchQuery}.
     */
    public static LocalDate arrivalDate = LocalDate.now();

    /**
     * The date intended for departure from the destination searched for with {@link SampleApp#searchQuery}.
     */
    public static LocalDate departureDate = arrivalDate.plusDays(1);

    /**
     * The cache key returned by the api for the current search.
     * Used for pagination through the found hotels that were not returned immediately by the api request.
     * Found using the api-lib class {@link com.ean.mobile.hotel.request.ListRequest}.
     */
    public static String cacheKey;

    /**
     * The cache location returned by the api for the current search.
     * Like {@link SampleApp#cacheKey}, used for pagination.
     * Found using the api-lib class {@link com.ean.mobile.hotel.request.ListRequest}.
     */
    public static String cacheLocation;

    /**
     * The hotel selected from {@link SampleApp#FOUND_HOTELS} for further inspection.
     * Should exist in {@link SampleApp#FOUND_HOTELS}.
     */
    public static Hotel selectedHotel;

    /**
     * The room that has been selected from the hotel information page that is intended to be
     * booked by the user.
     */
    public static HotelRoom selectedRoom;

    /**
     * The hotels found when searching with {@link SampleApp#searchQuery}.
     * Found using the api-lib class {@link com.ean.mobile.hotel.request.ListRequest}.
     */
    public static final List<Hotel> FOUND_HOTELS = new ArrayList<Hotel>();

    /**
     * The in-memory cache of extended hotel information, to make display of the HotelInformation page faster
     * for hotels that have already been loaded.
     */
    public static final Map<Long, HotelInformation> EXTENDED_INFOS
            = Collections.synchronizedMap(new HashMap<Long, HotelInformation>());

    /**
     * The rooms found for each hotel. The Keys are the hotelids for the hotels that have rooms found,
     * the values are the list of rooms for that hotel.
     */
    public static final Map<Long, List<HotelRoom>> HOTEL_ROOMS
            = Collections.synchronizedMap(new HashMap<Long, List<HotelRoom>>());

    /**
     * The in-memory cache of drawables found for HotelImageTuples. Utilizes {@link HotelImageDrawableMap}. Should
     * instead use some sort of tiered local caching mechanism, but for timing reasons, this remains a custom map
     * implementation based on {@link HashMap}.
     *
     * Note: The {@link HotelImageDrawableMap} will automatically add any HotelImageTuple as a key if it doesn't already
     * exist in the map on usage of .get() and then return the new HotelImageDrawable object constructed for it.
     */
    public static final Map<HotelImageTuple, HotelImageDrawable> IMAGE_DRAWABLES
            = Collections.synchronizedMap(new HotelImageDrawableMap());

    private static final Set<Reservation> RESERVATIONS = new TreeSet<Reservation>();


    private static final long TEN_MEGABYTES = 10 * 1024 * 1024;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate() {
        super.onCreate();
        setupHttpConnectionStuff();
        CommonParameters.cid = "55505";
        CommonParameters.apiKey = "cbrzfta369qwyrm9t5b8y8kf";
        CommonParameters.customerUserAgent = "Android";
        CommonParameters.locale = Locale.US.toString();
        CommonParameters.currencyCode = Currency.getInstance(Locale.US).getCurrencyCode();
    }

    /**
     * Gets the number of adults and children represented as a RoomOccupancy for easy use in the api.
     * @return The room occupancy directly representing the
     *          {@link SampleApp#numberOfAdults} and {@link SampleApp#childAges}.
     */
    public static RoomOccupancy occupancy() {
        return new RoomOccupancy(numberOfAdults, childAges);
    }

    /**
     * Sends a toast to the user stating that the request has been redirected, likely due to an issue with
     * their internet connection requiring login, and that they should check that as well.
     * @param context The context into which to display the toast. Should be the application context.
     */
    public static void sendRedirectionToast(final Context context) {
        Toast.makeText(context, R.string.redirected, Toast.LENGTH_LONG).show();
    }

    /**
     * Resets just the dates to the default, today and today + 1.
     */
    public static void resetDates() {
        arrivalDate = LocalDate.now();
        departureDate = arrivalDate.plusDays(1);
    }

    /**
     * Completely clears the search and sets the fields filled by a search to their defaults.
     */
    public static void clearSearch() {
        searchQuery = null;
        numberOfAdults = 0;
        childAges = null;
        resetDates();
        FOUND_HOTELS.clear();
        selectedHotel = null;
        selectedRoom = null;
        EXTENDED_INFOS.clear();
        HOTEL_ROOMS.clear();
        IMAGE_DRAWABLES.clear();
    }

    /**
     * Updates the {@link SampleApp#FOUND_HOTELS} list with new hotels as loaded from a request spawning from a list
     * request constructed by {@link com.ean.mobile.hotel.request.ListRequest#ListRequest(String, String)}, the
     * ean api's concept of "paging".
     * Overloads to {@link SampleApp#updateFoundHotels(com.ean.mobile.hotel.HotelList, boolean)}, with clearOnUpdate
     * set to false.
     * @param hotelList The list of hotels to add to {@link SampleApp#FOUND_HOTELS}.
     */
    public static void updateFoundHotels(final HotelList hotelList) {
        updateFoundHotels(hotelList, false);
    }

    /**
     * Adds the hotelList to the {@link SampleApp#FOUND_HOTELS} list, optionally clearing the extant contents. Also
     * applies the cacheKey and cacheLocation from the hotelList.
     * @param hotelList The list of hotels to add to {@link SampleApp#FOUND_HOTELS}.
     * @param clearOnUpdate Whether or not to clear {@link SampleApp#FOUND_HOTELS}.
     */
    public static synchronized void updateFoundHotels(final HotelList hotelList, final boolean clearOnUpdate) {
        if (clearOnUpdate) {
            SampleApp.FOUND_HOTELS.clear();
        }
        if (hotelList != null) {
            SampleApp.FOUND_HOTELS.addAll(hotelList.hotels);
            SampleApp.cacheKey = hotelList.cacheKey;
            SampleApp.cacheLocation = hotelList.cacheLocation;
        }
    }

    /**
     * Adds a reservation object to an application-persistent, in-memory cache of reservations, sorted by date.
     * @param reservation The reservation to save.
     */
    public static void addReservationToCache(final Reservation reservation) {
        RESERVATIONS.add(reservation);
    }

    /**
     * Gets the most recent reservation from the collection containing them.
     * @return The reservation requested, or null if there are no reservations.
     */
    public static Reservation getLatestReservation() {
        return RESERVATIONS.size() == 0 ? null : RESERVATIONS.iterator().next();
    }


    private void setupHttpConnectionStuff() {
        // exists due to advice found at http://android-developers.blogspot.com/2011/09/androids-http-clients.html.
        // HTTP connection reuse which was buggy pre-froyo
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
            System.setProperty("http.keepAlive", "false");
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            try {
                final File httpCacheDir = new File(getCacheDir(), "http");
                HttpResponseCache.install(httpCacheDir, TEN_MEGABYTES);
            } catch (IOException ioe) {
                Log.e(Constants.LOG_TAG, "Could not install http cache on this device.");
            }
        }
    }

}
