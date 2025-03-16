package org.hismeo.bendsanimator.client.util.ease;

import org.jetbrains.annotations.NotNull;

// MIT License
// Source form https://github.com/MasayukiSuda/EasingInterpolator/blob/master/ei/src/main/java/com/daasuu/ei/EasingProvider.java
public class EasingProvider {
    /**
     * @param ease            Easing type
     * @param elapsedTimeRate Elapsed time / Total time
     * @return easedValue
     */
    public float get(@NotNull Ease ease, float elapsedTimeRate) {
        return switch (ease) {
            case LINEAR -> elapsedTimeRate;
            case QUAD_IN -> getPowIn(elapsedTimeRate, 2);
            case QUAD_OUT -> getPowOut(elapsedTimeRate, 2);
            case QUAD_IN_OUT -> getPowInOut(elapsedTimeRate, 2);
            case CUBIC_IN -> getPowIn(elapsedTimeRate, 3);
            case CUBIC_OUT -> getPowOut(elapsedTimeRate, 3);
            case CUBIC_IN_OUT -> getPowInOut(elapsedTimeRate, 3);
            case QUART_IN -> getPowIn(elapsedTimeRate, 4);
            case QUART_OUT -> getPowOut(elapsedTimeRate, 4);
            case QUART_IN_OUT -> getPowInOut(elapsedTimeRate, 4);
            case QUINT_IN -> getPowIn(elapsedTimeRate, 5);
            case QUINT_OUT -> getPowOut(elapsedTimeRate, 5);
            case QUINT_IN_OUT -> getPowInOut(elapsedTimeRate, 5);
            case SINE_IN -> (float) (1f - Math.cos(elapsedTimeRate * Math.PI / 2f));
            case SINE_OUT -> (float) Math.sin(elapsedTimeRate * Math.PI / 2f);
            case SINE_IN_OUT -> (float) (-0.5f * (Math.cos(Math.PI * elapsedTimeRate) - 1f));
            case BACK_IN -> (float) (elapsedTimeRate * elapsedTimeRate * ((1.7 + 1f) * elapsedTimeRate - 1.7));
            case BACK_OUT -> (float) (--elapsedTimeRate * elapsedTimeRate * ((1.7 + 1f) * elapsedTimeRate + 1.7) + 1f);
            case BACK_IN_OUT -> getBackInOut(elapsedTimeRate, 1.7f);
            case CIRC_IN -> (float) -(Math.sqrt(1f - elapsedTimeRate * elapsedTimeRate) - 1);
            case CIRC_OUT -> (float) Math.sqrt(1f - (--elapsedTimeRate) * elapsedTimeRate);
            case CIRC_IN_OUT -> {
                if ((elapsedTimeRate *= 2f) < 1f) {
                    yield (float) (-0.5f * (Math.sqrt(1f - elapsedTimeRate * elapsedTimeRate) - 1f));
                }
                yield (float) (0.5f * (Math.sqrt(1f - (elapsedTimeRate -= 2f) * elapsedTimeRate) + 1f));
            }
            case BOUNCE_IN -> getBounceIn(elapsedTimeRate);
            case BOUNCE_OUT -> getBounceOut(elapsedTimeRate);
            case BOUNCE_IN_OUT -> {
                if (elapsedTimeRate < 0.5f) {
                    yield getBounceIn(elapsedTimeRate * 2f) * 0.5f;
                }
                yield getBounceOut(elapsedTimeRate * 2f - 1f) * 0.5f + 0.5f;
            }
            case ELASTIC_IN -> getElasticIn(elapsedTimeRate, 1, 0.3);
            case ELASTIC_OUT -> getElasticOut(elapsedTimeRate, 1, 0.3);
            case ELASTIC_IN_OUT -> getElasticInOut(elapsedTimeRate, 1, 0.45);
            case EASE_IN_EXPO -> (float) Math.pow(2, 10 * (elapsedTimeRate - 1));
            case EASE_OUT_EXPO -> (float) -Math.pow(2, -10 * elapsedTimeRate) + 1;
            case EASE_IN_OUT_EXPO -> {
                if ((elapsedTimeRate *= 2) < 1) {
                    yield (float) Math.pow(2, 10 * (elapsedTimeRate - 1)) * 0.5f;
                }
                yield (float) (-Math.pow(2, -10 * --elapsedTimeRate) + 2f) * 0.5f;
            }
            default -> elapsedTimeRate;
        };

    }

    /**
     * @param elapsedTimeRate Elapsed time / Total time
     * @param pow             pow The exponent to use (ex. 3 would return a cubic ease).
     * @return easedValue
     */
    private static float getPowIn(float elapsedTimeRate, double pow) {
        return (float) Math.pow(elapsedTimeRate, pow);
    }

    /**
     * @param elapsedTimeRate Elapsed time / Total time
     * @param pow             pow The exponent to use (ex. 3 would return a cubic ease).
     * @return easedValue
     */
    private static float getPowOut(float elapsedTimeRate, double pow) {
        return (float) ((float) 1 - Math.pow(1 - elapsedTimeRate, pow));
    }

    /**
     * @param elapsedTimeRate Elapsed time / Total time
     * @param pow             pow The exponent to use (ex. 3 would return a cubic ease).
     * @return easedValue
     */
    private static float getPowInOut(float elapsedTimeRate, double pow) {
        if ((elapsedTimeRate *= 2) < 1) {
            return (float) (0.5 * Math.pow(elapsedTimeRate, pow));
        }

        return (float) (1 - 0.5 * Math.abs(Math.pow(2 - elapsedTimeRate, pow)));
    }

    /**
     * @param elapsedTimeRate Elapsed time / Total time
     * @param amount          amount The strength of the ease.
     * @return easedValue
     */
    private static float getBackInOut(float elapsedTimeRate, float amount) {
        amount *= 1.525f;
        if ((elapsedTimeRate *= 2) < 1) {
            return (float) (0.5 * (elapsedTimeRate * elapsedTimeRate * ((amount + 1) * elapsedTimeRate - amount)));
        }
        return (float) (0.5 * ((elapsedTimeRate -= 2) * elapsedTimeRate * ((amount + 1) * elapsedTimeRate + amount) + 2));
    }

    /**
     * @param elapsedTimeRate Elapsed time / Total time
     * @return easedValue
     */
    private static float getBounceIn(float elapsedTimeRate) {
        return 1f - getBounceOut(1f - elapsedTimeRate);
    }

    /**
     * @param elapsedTimeRate Elapsed time / Total time
     * @return easedValue
     */
    private static float getBounceOut(float elapsedTimeRate) {
        if (elapsedTimeRate < 1 / 2.75) {
            return (float) (7.5625 * elapsedTimeRate * elapsedTimeRate);
        } else if (elapsedTimeRate < 2 / 2.75) {
            return (float) (7.5625 * (elapsedTimeRate -= 1.5f / 2.75f) * elapsedTimeRate + 0.75);
        } else if (elapsedTimeRate < 2.5 / 2.75) {
            return (float) (7.5625 * (elapsedTimeRate -= 2.25f / 2.75f) * elapsedTimeRate + 0.9375);
        } else {
            return (float) (7.5625 * (elapsedTimeRate -= 2.625f / 2.75f) * elapsedTimeRate + 0.984375);
        }
    }

    /**
     * @param elapsedTimeRate Elapsed time / Total time
     * @param amplitude       Amplitude of easing
     * @param period          Animation of period
     * @return easedValue
     */
    private static float getElasticIn(float elapsedTimeRate, double amplitude, double period) {
        if (elapsedTimeRate == 0 || elapsedTimeRate == 1) return elapsedTimeRate;
        double pi2 = Math.PI * 2;
        double s = period / pi2 * Math.asin(1 / amplitude);
        return (float) -(amplitude * Math.pow(2f, 10f * (elapsedTimeRate -= 1f)) * Math.sin((elapsedTimeRate - s) * pi2 / period));
    }

    /**
     * @param elapsedTimeRate Elapsed time / Total time
     * @param amplitude       Amplitude of easing
     * @param period          Animation of period
     * @return easedValue
     */
    private static float getElasticOut(float elapsedTimeRate, double amplitude, double period) {
        if (elapsedTimeRate == 0 || elapsedTimeRate == 1) return elapsedTimeRate;

        double pi2 = Math.PI * 2;
        double s = period / pi2 * Math.asin(1 / amplitude);
        return (float) (amplitude * Math.pow(2, -10 * elapsedTimeRate) * Math.sin((elapsedTimeRate - s) * pi2 / period) + 1);
    }

    /**
     * @param elapsedTimeRate Elapsed time / Total time
     * @param amplitude       Amplitude of easing
     * @param period          Animation of period
     * @return easedValue
     */
    private static float getElasticInOut(float elapsedTimeRate, double amplitude, double period) {
        double pi2 = Math.PI * 2;

        double s = period / pi2 * Math.asin(1 / amplitude);
        if ((elapsedTimeRate *= 2) < 1) {
            return (float) (-0.5f * (amplitude * Math.pow(2, 10 * (elapsedTimeRate -= 1f)) * Math.sin((elapsedTimeRate - s) * pi2 / period)));
        }
        return (float) (amplitude * Math.pow(2, -10 * (elapsedTimeRate -= 1)) * Math.sin((elapsedTimeRate - s) * pi2 / period) * 0.5 + 1);

    }
}
