package com.example.geojeroserver.engine;

import java.util.List;

public record Trip(long tripId, String routeNo, int direction, String headsign,
                   String noteRaw, List<TripStop> stops) {}
