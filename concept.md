Totally fair. Let’s go “concept-only but rich”—a Level-1.5 map with 9 high-level concepts (no engine names, no JIL/YAML) and simple relationships. You can import this straight into draw.io.

What’s inside

Catalog – Dataset definitions

Dataset instances – snapshots

Process definition – workflow

Step definition – optional concept

I/O contract

Lineage – dataset and column

Dependencies – process and dataset

Observability – SLA and milestones

Governance – ownership and policy


Draw.io XML (clean, ASCII-only)

Import via File → Import → XML in diagrams.net:

<mxfile host="app.diagrams.net">
  <diagram id="conceptV15" name="Concept Map v1.5">
    <mxGraphModel dx="1200" dy="800" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1600" pageHeight="1200" math="0" shadow="0">
      <root>
        <mxCell id="0"/>
        <mxCell id="1" parent="0"/>

        <!-- Row 1: Governance, Lineage, Observability -->
        <mxCell id="governance" value="Governance - ownership and policy" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontStyle=1" vertex="1" parent="1">
          <mxGeometry x="60" y="60" width="280" height="80" as="geometry"/>
        </mxCell>
        <mxCell id="lineage" value="Lineage - dataset and column" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;fontStyle=1" vertex="1" parent="1">
          <mxGeometry x="360" y="60" width="280" height="80" as="geometry"/>
        </mxCell>
        <mxCell id="observability" value="Observability - SLA and milestones" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;fontStyle=1" vertex="1" parent="1">
          <mxGeometry x="660" y="60" width="320" height="80" as="geometry"/>
        </mxCell>

        <!-- Row 2: Catalog, Process, I/O, Step -->
        <mxCell id="catalog" value="Catalog - dataset definitions" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;fontStyle=1" vertex="1" parent="1">
          <mxGeometry x="60" y="200" width="320" height="100" as="geometry"/>
        </mxCell>
        <mxCell id="process" value="Process definition - workflow" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#ffe6cc;strokeColor=#d79b00;fontStyle=1" vertex="1" parent="1">
          <mxGeometry x="420" y="200" width="320" height="100" as="geometry"/>
        </mxCell>
        <mxCell id="io" value="I/O contract" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;fontStyle=1" vertex="1" parent="1">
          <mxGeometry x="780" y="200" width="260" height="100" as="geometry"/>
        </mxCell>
        <mxCell id="stepdef" value="Step definition - optional concept" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#f4f4f4;strokeColor=#999999;fontStyle=1" vertex="1" parent="1">
          <mxGeometry x="1080" y="200" width="280" height="100" as="geometry"/>
        </mxCell>

        <!-- Row 3: Instances and Dependencies -->
        <mxCell id="instances" value="Dataset instances - snapshots" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#cfe2f3;strokeColor=#6fa8dc;fontStyle=1" vertex="1" parent="1">
          <mxGeometry x="60" y="360" width="320" height="100" as="geometry"/>
        </mxCell>
        <mxCell id="dependencies" value="Dependencies - process and dataset" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#ead1dc;strokeColor=#a64d79;fontStyle=1" vertex="1" parent="1">
          <mxGeometry x="420" y="360" width="640" height="100" as="geometry"/>
        </mxCell>

        <!-- Edges (high-level relations) -->
        <mxCell id="e1" edge="1" parent="1" source="governance" target="catalog" style="endArrow=block;strokeColor=#6c8ebf">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>
        <mxCell id="e2" edge="1" parent="1" source="governance" target="process" style="endArrow=block;strokeColor=#6c8ebf">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <mxCell id="e3" edge="1" parent="1" source="lineage" target="catalog" style="endArrow=block;strokeColor=#9673a6">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <mxCell id="e4" edge="1" parent="1" source="catalog" target="instances" style="endArrow=block;strokeColor=#82b366">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <mxCell id="e5" edge="1" parent="1" source="process" target="io" style="endArrow=block;strokeColor=#d79b00">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>
        <mxCell id="e6" edge="1" parent="1" source="io" target="catalog" style="endArrow=block;strokeColor=#b85450">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>
        <mxCell id="e7" edge="1" parent="1" source="process" target="stepdef" style="endArrow=block;strokeColor=#d79b00">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <mxCell id="e8" edge="1" parent="1" source="observability" target="process" style="endArrow=block;strokeColor=#d6b656">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>
        <mxCell id="e9" edge="1" parent="1" source="observability" target="instances" style="endArrow=block;strokeColor=#d6b656">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <mxCell id="e10" edge="1" parent="1" source="dependencies" target="process" style="endArrow=block;strokeColor=#a64d79">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>
        <mxCell id="e11" edge="1" parent="1" source="dependencies" target="instances" style="endArrow=block;strokeColor=#a64d79">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>

How this “concept-only but rich” map reads

Catalog defines what data exists; Instances are the snapshots you actually use.

Process transforms data; I/O contract states what it expects and produces.

Step definition is optional concept to show a process may include multiple steps (no engine details here).

Lineage describes how datasets relate at dataset and column level.

Dependencies capture readiness and ordering across processes and datasets.

Observability watches both processes and dataset instances for SLA and milestones.

Governance overlays ownership and policy on processes and datasets.


If you want, I can also produce a matching Level-2 container diagram that expands only one of these boxes (e.g., Process) into its pieces (Profile, IO, Steps, Binding) while keeping the rest at concept level—useful for reviews without overwhelming the audience.

