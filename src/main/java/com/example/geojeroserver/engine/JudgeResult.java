package com.example.geojeroserver.engine;

import java.util.List;

public record JudgeResult(Verdict feasible, List<LegResult> legs) {}
