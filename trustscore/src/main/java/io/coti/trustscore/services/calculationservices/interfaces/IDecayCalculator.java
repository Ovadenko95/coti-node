package io.coti.trustscore.services.calculationservices.interfaces;

import io.coti.trustscore.config.rules.EventScore;

import java.util.Map;

public interface IDecayCalculator {
    <T extends EventScore, S> Map<T, S> calculate(int numberOfDecays);
}


