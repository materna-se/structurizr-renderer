package io.github.stephanpirnbaum.structurizr.renderer.plantuml;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The layout strategy to be used when PlantUML is used as renderer.
 *
 * @author Stephan Pirnbaum
 */
@Getter
@RequiredArgsConstructor
public enum PlantumlLayoutEngine {

    ELK("elk"),
    GRAPHVIZ("graphviz"),
    SMETANA("smetana");

    private final String representation;


}
