package com.zinterr.todagodrive.model

import com.google.gson.annotations.SerializedName

// USER ACCOUNT DETAILS MODEL
data class Account(
    val uid: String? = null,
    val type: String? = null,
    val name: String? = null,
    var route: String? = null,
    var verified: Boolean? = null
)

data class User(
    val uid: String? = null,
    val name: String? = null,
    var phone: String? = null,
    val plate: String? = null,
    var location: String? = null,
    var email: String? = null,
    var assoc: String? = null,
    var rating: Float? = null,
    var rates: Int? = null,
    var verified: Boolean? = null,
    var emailVerified: Boolean? = null,
    var profile: String? = null,
    var license: String? = null,
    var permit: String? = null,
    var document: String? = null,
    var matrix: String? = null,
    val status: String? = null
)

// APP UPDATES MODEL
data class App (
    val todaGoDriveVersion: String? = null,
    val todaGoDriveRequired: Boolean? = null,
    val todaGoDriveLink: String? = null,
    val todaGoDriveLog: String? = null
)

// ASSOCIATION PRICE MATRIX MODEL
data class Matrix(
    val name: String? = null,
    val discount: Int? = null,
    val perKm: Float? = null,
    val regular: Int? = null,
    val special: Int? = null,
    val specialPerKM: Float? = null
)

// RIDE DETAILS MODEL
data class Commuter(
    val uid: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val rate: String? = null,
    val passenger: Int? = null,
    val start: String? = null,
    val startAddress: String? = null,
    val end: String? = null,
    val endAddress: String? = null,
    val current: String? = null,
    val distance: Int? = null,
    val duration: Int? = null,
    val status: String? = null,
    val dateTime: String? = null,
    val weight: Int? = null,
    val discount: String? = null,
    val price: Int? = null
)

data class RideDriver(
    val uid: String? = null,
    var name: String? = null,
    var plate: String? = null,
    val rate: String? = null,
    val current: String? = null,
    val phone: String? = null,
    var profile: String? = null,
    val matrix: Matrix? = null,
    var busy: Boolean? = null,
    var active: Boolean? = null,
    val assoc: String? = null,
    var dateTime: String? = null
)

data class Ride(
    val uid: String? = null,
    val solo: Boolean? = null,
    val passenger: Int? = null,
    val commuter: Map<String, Commuter>? = null,
    var status: String? = null,
    val reference: String? = null,
    val dateTime: String? = null,
    val weight: Int? = null,
    val driver: RideDriver? = null
)

// HISTORY DETAILS MODEL
data class History(
    val uid: String? = null,
    val solo: Boolean? = null,
    val start: String? = null,
    val end: String? = null,
    val passenger: Int? = null,
    val price: Int? = null,
    val dateTime: String? = null,
    var driver: String? = null
)

// DIRECTIONS API RESPONSE MODEL
data class DirectionsResponse(val routes: List<Route>)

data class Route(
    @SerializedName("bounds") val bounds: RouteBounds,
    val legs: List<Leg>
)

data class RouteBounds(
    @SerializedName("northeast") val northeast: LatLngLiteral,
    @SerializedName("southwest") val southwest: LatLngLiteral,
)

data class Leg(
    @SerializedName("start_location") val start: LatLngLiteral,
    @SerializedName("end_location") val end: LatLngLiteral,
    @SerializedName("distance") val distance: ValueText,
    @SerializedName("duration") val duration: ValueText,
    val steps: List<Step>
)

data class Step(val polyline: PolylinePoints)

data class ValueText(val text: String, val value: Int)

data class PolylinePoints(val points: String)

data class LatLngLiteral(val lat: Double, val lng: Double)

// GEOCODE API RESPONSE MODEL
data class GeocodeResponse(
    val results: List<GeocodeResult>
)

data class GeocodeResult(
    @SerializedName("address_components") val addressComponents: List<AddressComponent>
)

data class AddressComponent(
    @SerializedName("long_name") val longName: String,
    val types: List<String>
)

// GEOCODE API RESPONSE PARSING MODEL
data class LocalAddress(
    val province: String? = null,
    val city: String? = null,
    val locality: String? = null,
    val extra: String? = null
)

// ROUTES API REQUEST MODEL
data class RoutesRequest(
    val origin: Waypoint,
    val destination: Waypoint,
    val departureTime: String? = null,
    val intermediates: List<Waypoint>? = null,
    val travelMode: String = "DRIVE",
    val routingPreference: String = "TRAFFIC_AWARE",
    val polylineQuality: String = "OVERVIEW",
    val polylineEncoding: String = "ENCODED_POLYLINE",
    val languageCode: String = "en-US",
    val units: String = "METRIC",
    val optimizeWaypointOrder: Boolean = true,
    val routeModifiers: RouteModifiers = RouteModifiers()
)

data class Waypoint(
    val location: LocationWrapper? = null,
    val via: Boolean = false,
    val vehicleStopover: Boolean = true,
    val sideOfRoad: Boolean = true,
)

data class RouteModifiers(
    val avoidTolls: Boolean = true,
    val avoidHighways: Boolean = true,
    val avoidFerries: Boolean = true
)

data class LocationWrapper(val location: LatLngWrapper)

data class LatLngWrapper(val latLng: RoutesLatLng)

data class RoutesLatLng(val latitude: Double, val longitude: Double)

// ROUTES API RESPONSE MODEL
data class RoutesResponse(val routes: List<RoutesRoute>?)

data class RoutesRoute(
    val legs: List<RouteLeg>? = null,
    val distanceMeters: Int? = null,
    val duration: String? = null,
    val staticDuration: String? = null,
    val polyline: RoutesPolyline? = null,
    val viewport: Viewport? = null,
)

data class RouteLeg(
    val distanceMeters: Int? = null,
    val duration: String? = null,
    val staticDuration: String? = null,
    val startLocation: LocationWrapper? = null,
    val endLocation: LocationWrapper? = null
)

data class RoutesPolyline(
    val encodedPolyline: String? = null
)

data class Viewport(
    val low: RoutesLatLng? = null,
    val high: RoutesLatLng? = null
)

data class DistanceMatrixResponse(val rows: List<Row>?)

data class Row(val elements: List<Element>?)

data class Element(val distance: Distance?, val duration: Duration?)

data class Distance(val text: String?, val value: Int?)

data class Duration(val text: String?, val value: Int?)
