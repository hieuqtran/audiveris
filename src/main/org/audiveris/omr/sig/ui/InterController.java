//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                  I n t e r C o n t r o l l e r                                 //
//                                                                                                //
//------------------------------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">
//
//  Copyright © Audiveris 2019. All rights reserved.
//
//  This program is free software: you can redistribute it and/or modify it under the terms of the
//  GNU Affero General Public License as published by the Free Software Foundation, either version
//  3 of the License, or (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
//  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//  See the GNU Affero General Public License for more details.
//
//  You should have received a copy of the GNU Affero General Public License along with this
//  program.  If not, see <http://www.gnu.org/licenses/>.
//------------------------------------------------------------------------------------------------//
// </editor-fold>
package org.audiveris.omr.sig.ui;

import ij.process.ByteProcessor;

import org.audiveris.omr.OMR;
import org.audiveris.omr.constant.Constant;
import org.audiveris.omr.constant.ConstantSet;
import org.audiveris.omr.glyph.Glyph;
import org.audiveris.omr.glyph.GlyphFactory;
import org.audiveris.omr.glyph.Shape;
import org.audiveris.omr.glyph.ui.NestView;
import org.audiveris.omr.glyph.ui.SymbolsEditor;
import org.audiveris.omr.math.AreaUtil;
import org.audiveris.omr.math.GeoUtil;
import org.audiveris.omr.math.LineUtil;
import org.audiveris.omr.math.PointUtil;
import org.audiveris.omr.score.Page;
import org.audiveris.omr.sheet.PartBarline;
import org.audiveris.omr.sheet.Sheet;
import org.audiveris.omr.sheet.Skew;
import org.audiveris.omr.sheet.Staff;
import org.audiveris.omr.sheet.SystemInfo;
import org.audiveris.omr.sheet.rhythm.MeasureStack;
import org.audiveris.omr.sheet.symbol.InterFactory;
import org.audiveris.omr.sheet.ui.BookActions;
import org.audiveris.omr.sig.SIGraph;
import org.audiveris.omr.sig.inter.BarlineInter;
import org.audiveris.omr.sig.inter.BarConnectorInter;
import org.audiveris.omr.sig.inter.ChordNameInter;
import org.audiveris.omr.sig.inter.HeadChordInter;
import org.audiveris.omr.sig.inter.HeadInter;
import org.audiveris.omr.sig.inter.Inter;
import org.audiveris.omr.sig.inter.InterEnsemble;
import org.audiveris.omr.sig.inter.Inters;
import org.audiveris.omr.sig.inter.LyricItemInter;
import org.audiveris.omr.sig.inter.LyricLineInter;
import org.audiveris.omr.sig.inter.RestChordInter;
import org.audiveris.omr.sig.inter.RestInter;
import org.audiveris.omr.sig.inter.SentenceInter;
import org.audiveris.omr.sig.inter.SlurInter;
import org.audiveris.omr.sig.inter.StaffBarlineInter;
import org.audiveris.omr.sig.inter.StemInter;
import org.audiveris.omr.sig.inter.WordInter;
import org.audiveris.omr.sig.relation.AugmentationRelation;
import org.audiveris.omr.sig.relation.BarConnectionRelation;
import org.audiveris.omr.sig.relation.BeamStemRelation;
import org.audiveris.omr.sig.relation.ChordStemRelation;
import org.audiveris.omr.sig.relation.Containment;
import org.audiveris.omr.sig.relation.FlagStemRelation;
import org.audiveris.omr.sig.relation.HeadStemRelation;
import org.audiveris.omr.sig.relation.Link;
import org.audiveris.omr.sig.relation.MirrorRelation;
import org.audiveris.omr.sig.relation.Relation;
import org.audiveris.omr.sig.relation.SlurHeadRelation;
import org.audiveris.omr.sig.relation.Support;
import org.audiveris.omr.sig.ui.UITask.OpKind;
import static org.audiveris.omr.sig.ui.UITask.OpKind.*;
import org.audiveris.omr.sig.ui.UITaskList.Option;
import org.audiveris.omr.step.Step;
import org.audiveris.omr.text.BlockScanner;
import org.audiveris.omr.text.OCR;
import org.audiveris.omr.text.OcrUtil;
import org.audiveris.omr.text.TextBuilder;
import org.audiveris.omr.text.TextLine;
import org.audiveris.omr.text.TextRole;
import org.audiveris.omr.text.TextWord;
import org.audiveris.omr.ui.selection.EntityListEvent;
import org.audiveris.omr.ui.selection.MouseMovement;
import org.audiveris.omr.ui.selection.SelectionHint;
import org.audiveris.omr.ui.util.UIThread;
import org.audiveris.omr.util.Entities;
import org.audiveris.omr.util.HorizontalSide;
import static org.audiveris.omr.util.HorizontalSide.*;
import org.audiveris.omr.util.VoidTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * Class {@code InterController} is the UI in charge of dealing with Inter and
 * Relation instances (addition, removal, modifications) to correct OMR output,
 * with the ability to undo and redo at will.
 * <p>
 * It works at sheet level.
 * <p>
 * When adding or dropping an inter, the instance is allocated in proper system (and staff if
 * relevant) together with its relations with existing partners nearby.
 * It is not always obvious to select the proper staff, various techniques are used, and if
 * all have failed, the user is prompted for staff indication.
 * <p>
 * Finally, a proper {@link UITaskList} is allocated, inserted in controller's history, and run.
 * Undo and Redo actions operate on this history.
 * <p>
 * User actions are processed asynchronously in background.
 *
 * @author Hervé Bitteur
 */
public class InterController
{

    private static final Constants constants = new Constants();

    private static final Logger logger = LoggerFactory.getLogger(InterController.class);

    /** Underlying sheet. */
    private final Sheet sheet;

    /** History of tasks. */
    private final TaskHistory history = new TaskHistory();

    /** User pane. Lazily assigned */
    private SymbolsEditor editor;

    /**
     * Creates a new {@code IntersController} object.
     *
     * @param sheet the underlying sheet
     */
    public InterController (Sheet sheet)
    {
        this.sheet = sheet;
    }

    //-----------//
    // addInters //
    //-----------//
    /**
     * Add one or several inters.
     *
     * @param inters  the populated inters (staff and bounds are already set)
     * @param options additional options
     */
    @UIThread
    public void addInters (final List<? extends Inter> inters,
                           final Option... options)
    {
        if ((inters == null) || inters.isEmpty()) {
            return;
        }

        if ((options == null) || !Arrays.asList(options).contains(Option.VALIDATED)) {
            if (sheet.getStub().getLatestStep().compareTo(Step.MEASURES) >= 0) {
                List<Inter> staffBarlines = staffBarlinesOf(inters);

                if (!staffBarlines.isEmpty()) {
                    final StaffBarlineInter oneBar = (StaffBarlineInter) staffBarlines.get(0);
                    final List<Inter> closure = buildStaffBarlineClosure(oneBar);

                    if (!closure.isEmpty()) {
                        addInters(closure, Option.VALIDATED, Option.UPDATE_MEASURES);
                    }

                    return;
                }
            }
        }

        new CtrlTask(DO, "addInters", options)
        {
            private final List<LinkedGhost> linkedGhosts = new ArrayList<>();

            @Override
            protected void action ()
            {

                for (Inter inter : inters) {
                    SystemInfo system = inter.getStaff().getSystem();

                    if (inter instanceof BarlineInter) {
                        BarlineInter b = (BarlineInter) inter;

                        if (b.getArea() == null) {
                            b.setArea(new Area(b.getBounds()));
                        }
                    }

                    // If glyph is used by another inter, delete this other inter
                    removeCompetitors(inter, inter.getGlyph(), system, seq);

                    linkedGhosts.add(new LinkedGhost(inter, inter.searchLinks(system, false)));
                }

                addGhosts(seq, linkedGhosts);
            }

            @Override
            protected void publish ()
            {
                sheet.getInterIndex().publish(linkedGhosts.get(0).ghost);
                sheet.getGlyphIndex().publish(null);
            }

        }.execute();
    }

    //-------------//
    // assignGlyph //
    //-------------//
    /**
     * Add a shape interpretation based on a provided glyph.
     *
     * @param aGlyph the glyph to interpret
     * @param shape  the shape to be assigned
     */
    @UIThread
    public void assignGlyph (Glyph aGlyph,
                             final Shape shape)
    {
        if ((shape == Shape.TEXT) || (shape == Shape.LYRICS)) {
            addText(aGlyph, shape);

            return;
        }

        final Glyph glyph = sheet.getGlyphIndex().registerOriginal(aGlyph);
        final Inter ghost = InterFactory.createManual(shape, sheet);
        ghost.setBounds(glyph.getBounds());
        ghost.setGlyph(glyph);

        // While we are still interacting with the user, make sure we have the target staff
        final Collection<Link> links = new ArrayList<>();
        final Staff staff = determineStaff(glyph, ghost, links);

        if (staff == null) {
            logger.info("No staff, abandonned.");

            return;
        }

        // For barlines, make sure length is only one-staff high
        if (ghost instanceof BarlineInter || ghost instanceof StaffBarlineInter) {
            Rectangle box = ghost.getBounds();
            int y1 = staff.getFirstLine().yAt(box.x);
            int y2 = staff.getLastLine().yAt(box.x);
            ghost.setBounds(new Rectangle(box.x, y1, box.width, y2 - y1 + 1));
            ghost.setGlyph(null);
        }

        ghost.setStaff(staff);
        addInters(Arrays.asList(ghost));
    }

    //---------//
    // canRedo //
    //---------//
    /**
     * Is a redo possible?
     *
     * @return true if so
     */
    @UIThread
    public boolean canRedo ()
    {
        return history.canRedo();
    }

    //---------//
    // canUndo //
    //---------//
    /**
     * Is an undo possible?
     *
     * @return true if so
     */
    @UIThread
    public boolean canUndo ()
    {
        return history.canUndo();
    }

    //----------------//
    // changeSentence //
    //----------------//
    /**
     * Change the role of a sentence.
     *
     * @param sentence the sentence to modify
     * @param newRole  the new role for the sentence
     */
    @UIThread
    public void changeSentence (final SentenceInter sentence,
                                final TextRole newRole)
    {
        new CtrlTask(DO, "changeSentence")
        {
            @Override
            protected void action ()
            {
                seq.add(new SentenceRoleTask(sentence, newRole));
            }

            @Override
            protected void publish ()
            {
                sheet.getInterIndex().publish(sentence);
            }
        }.execute();
    }

    //------------//
    // changeWord //
    //------------//
    /**
     * Change the text content of a word.
     *
     * @param word     the word to modify
     * @param newValue the new word content
     */
    @UIThread
    public void changeWord (final WordInter word,
                            final String newValue)
    {
        new CtrlTask(DO, "changeWord")
        {
            @Override
            protected void action ()
            {
                seq.add(new WordValueTask(word, newValue));
            }

            @Override
            protected void publish ()
            {
                sheet.getInterIndex().publish(word);
            }
        }.execute();
    }

    //--------------//
    // clearHistory //
    //--------------//
    /**
     * Clear history of user actions.
     */
    @UIThread
    public void clearHistory ()
    {
        history.clear();

        if (editor != null) {
            refreshUI();
        }
    }

    //------//
    // link //
    //------//
    /**
     * Add a relation between inters.
     *
     * @param sig      the containing SIG
     * @param src      the source inter
     * @param target   the target inter
     * @param relation the relation to add
     */
    @UIThread
    public void link (final SIGraph sig,
                      final Inter src,
                      final Inter target,
                      final Relation relation)
    {
        new CtrlTask(DO, "link")
        {
            @Override
            protected void action ()
            {
                Inter source = src; // To allow use of a new source

                if (relation instanceof HeadStemRelation) {
                    HeadInter head = (HeadInter) source;

                    if (head.getChord() != null) {
                        source = preHeadStemLink(seq, head, (StemInter) target);
                    }
                }

                // Remove conflicting relations if any
                final boolean sourceIsNew = source != src;
                removeConflictingRelations(seq, sig, sourceIsNew, source, target, relation);

                // Finally, add relation
                seq.add(new LinkTask(sig, source, target, relation));
            }
        }.execute();
    }

    //-------------//
    // mergeChords //
    //-------------//
    /**
     * Make a single chord out of the provided two (or more) head chords.
     *
     * @param chords   the head chords to merge
     * @param withStem true for a merge with stem-based head chords, false for whole head chords
     */
    @UIThread
    public void mergeChords (final List<HeadChordInter> chords,
                             final boolean withStem)
    {
        new CtrlTask(DO, "mergeChords")
        {
            private final HeadChordInter newChord = new HeadChordInter(1.0);

            @Override
            protected void action ()
            {
                final SIGraph sig = chords.get(0).getSig();
                final Rectangle newChordBounds = Entities.getBounds(chords);

                // All heads involved
                final List<HeadInter> heads = new ArrayList<>();

                for (HeadChordInter ch : chords) {
                    for (Inter iHead : ch.getNotes()) {
                        heads.add((HeadInter) iHead);
                    }
                }

                Collections.sort(heads, Inters.byReverseCenterOrdinate);

                // Create a new chord ensemble will all heads
                final List<Link> newChordLinks = new ArrayList<>();
                for (HeadInter head : heads) {
                    newChordLinks.add(new Link(head, new Containment(), true));
                }

                // Transfer original chords support relations to the compound chord
                for (HeadChordInter ch : chords) {
                    for (Relation rel : sig.getRelations(ch, Support.class)) {
                        Inter target = sig.getEdgeTarget(rel);
                        Inter other = sig.getOppositeInter(ch, rel);
                        newChordLinks.add(new Link(other, rel.duplicate(), other == target));
                        seq.add(new UnlinkTask(sig, rel));
                    }
                }

                newChord.setManual(true);
                seq.add(new AdditionTask(sig, newChord, newChordBounds, newChordLinks));

                // Unlink each head from its original chord
                for (HeadChordInter ch : chords) {
                    for (Relation rel : sig.getRelations(ch, Containment.class)) {
                        seq.add(new UnlinkTask(sig, rel));
                    }
                }

                if (withStem) {
                    // Build the new stem linked to all heads
                    final List<StemInter> stems = new ArrayList<>();
                    final StemInter newStem = buildStem(chords, stems);
                    final Rectangle newStemBounds = Entities.getBounds(stems);

                    final List<Link> newStemLinks = new ArrayList<>();
                    for (HeadInter head : heads) {
                        newStemLinks.add(new Link(head, new HeadStemRelation(), false));
                    }

                    // Transfer original stem relations (beam, flag) to the compound stem
                    for (StemInter st : stems) {
                        for (Relation rel : sig.getRelations(st, BeamStemRelation.class,
                                                             FlagStemRelation.class)) {
                            Inter target = sig.getEdgeTarget(rel);
                            Inter other = sig.getOppositeInter(st, rel);
                            newStemLinks.add(new Link(other, rel.duplicate(), other == target));
                        }
                    }

                    seq.add(new AdditionTask(sig, newStem, newStemBounds, newStemLinks));

                    // Remove the original stems (and their relations)
                    for (StemInter stem : stems) {
                        seq.add(new RemovalTask(stem));
                    }
                }

                // Remove the original chords (and their relations)
                for (HeadChordInter ch : chords) {
                    seq.add(new RemovalTask(ch));
                }

                logger.debug("Merge {}", seq);
            }

            @Override
            protected void publish ()
            {
                newChord.countDots();
                sheet.getInterIndex().publish(newChord);
            }
        }.execute();
    }

    //-------------//
    // mergeSystem //
    //-------------//
    /**
     * Merge the provided system with its sibling below.
     *
     * TODO: Implement the UNDO side of this task, (if relevant)
     *
     * @param system the system above
     */
    @UIThread
    public void mergeSystem (final SystemInfo system)
    {
        new CtrlTask(DO, "mergeSystem")
        {
            @Override
            protected void action ()
            {
                final Staff upStaff = system.getLastStaff();
                final BarlineInter upBar = upStaff.getSideBarline(LEFT);

                final List<SystemInfo> systems = sheet.getSystems();
                final SystemInfo systemBelow = systems.get(1 + systems.indexOf(system));
                final Staff downStaff = systemBelow.getFirstStaff();
                final BarlineInter downBar = downStaff.getSideBarline(LEFT);

                // Merge the systems into one
                seq.add(new SystemMergeTask(system));

                if (upBar != null && downBar != null) {
                    // Add connector between up & down bars
                    Shape shape = (upBar.getShape() == Shape.THICK_BARLINE) ? Shape.THICK_CONNECTOR
                            : Shape.THIN_CONNECTOR;
                    Point2D p1 = upBar.getMedian().getP2();
                    Point2D p2 = downBar.getMedian().getP1();
                    Line2D median = new Line2D.Double(p1, p2);
                    double width = (upBar.getWidth() + downBar.getWidth()) * 0.5;
                    BarConnectorInter connector = new BarConnectorInter(shape, 1.0, median, width);
                    SIGraph sig = system.getSig();
                    seq.add(new AdditionTask(
                            sig, connector, connector.getBounds(), Collections.EMPTY_SET));

                    // Link up & down bars
                    seq.add(new LinkTask(sig, upBar, downBar, new BarConnectionRelation()));
                }
            }
        }.execute();
    }

    //-----------//
    // buildStem //
    //-----------//
    /**
     * Build a compound stem out of the provided stem-based head chords.
     *
     * @param chords the provided head chords
     * @param stems  (output) the original chords stems
     * @return the created compound stem
     */
    private StemInter buildStem (List<HeadChordInter> chords,
                                 List<StemInter> stems)
    {
        List<Glyph> glyphs = new ArrayList<>();

        for (HeadChordInter ch : chords) {
            StemInter stem = ch.getStem();
            stems.add(stem);

            if (stem.getGlyph() != null) {
                glyphs.add(stem.getGlyph());
            }
        }

        Collections.sort(stems, Inters.byCenterOrdinate);

        Glyph stemGlyph = glyphs.isEmpty() ? null : sheet.getGlyphIndex().registerOriginal(
                GlyphFactory.buildGlyph(glyphs));
        StemInter stemInter = new StemInter(stemGlyph, 1.0);
        stemInter.setManual(true);

        return stemInter;
    }

    //------//
    // redo //
    //------//
    /**
     * Redo last user (undone) action sequence.
     */
    @UIThread
    public void redo ()
    {
        new CtrlTask(REDO, "redo")
        {
            @Override
            protected void action ()
            {
                seq = history.toRedo();
            }

            @Override
            protected void publish ()
            {
                sheet.getInterIndex().publish(null);
            }
        }.execute();
    }

    //--------------//
    // removeInters //
    //--------------//
    /**
     * Remove the provided collection of inter (with their relations)
     *
     * @param inters  the inters to remove
     * @param options added options if any
     */
    @UIThread
    public void removeInters (final List<? extends Inter> inters,
                              final Option... options)
    {
        if ((options == null) || !Arrays.asList(options).contains(Option.VALIDATED)) {
            if (sheet.getStub().getLatestStep().compareTo(Step.MEASURES) >= 0) {
                // Now that measures exist, it's whole system height or nothing
                List<Inter> staffBarlines = new ArrayList<>(staffBarlinesOf(inters));

                if (staffBarlines.isEmpty()) {
                    for (Inter inter : barlinesOf(inters)) {
                        StaffBarlineInter sb = ((BarlineInter) inter).getStaffBarline();

                        if ((sb != null) && !staffBarlines.contains(sb)) {
                            staffBarlines.add(sb);
                        }
                    }
                }

                if (!staffBarlines.isEmpty()) {
                    final StaffBarlineInter oneBar = (StaffBarlineInter) staffBarlines.get(0);
                    final List<Inter> closure = getStaffBarlineClosure(oneBar);

                    if (!closure.isEmpty()) {
                        // Remove full system height
                        for (Inter inter : closure) {
                            inter.getBounds();
                        }

                        removeInters(closure, Option.VALIDATED, Option.UPDATE_MEASURES);
                    }

                    return;
                }
            }
        }

        new CtrlTask(DO, "removeInters", options)
        {
            @Override
            protected void action ()
            {
                populateRemovals(inters, seq);
            }

            @Override
            protected void publish ()
            {
                sheet.getInterIndex().publish(null);
            }
        }.execute();
    }

    //---------------------//
    // reprocessPageRhythm //
    //---------------------//
    /**
     * Reprocess the rhythm on the whole provided page.
     *
     * @param page page to reprocess
     */
    @UIThread
    public void reprocessPageRhythm (final Page page)
    {
        new CtrlTask(DO, "reprocessPageRhythm", Option.NO_HISTORY)
        {
            @Override
            protected void action ()
            {
                seq.add(new PageTask(page));
            }

            @Override
            protected Step firstImpactedStep ()
            {
                return Step.RHYTHMS;
            }
        }.execute();
    }

    //----------------------//
    // reprocessStackRhythm //
    //----------------------//
    /**
     * Reprocess the rhythm on the provided measure stack.
     *
     * @param stack measure stack to reprocess
     */
    @UIThread
    public void reprocessStackRhythm (final MeasureStack stack)
    {
        new CtrlTask(DO, "reprocessStackRhythm", Option.NO_HISTORY)
        {
            @Override
            protected void action ()
            {
                seq.add(new StackTask(stack));
            }

            @Override
            protected Step firstImpactedStep ()
            {
                return Step.RHYTHMS;
            }

        }.execute();
    }

    //------------------//
    // setSymbolsEditor //
    //------------------//
    /**
     * Late assignment of editor, to avoid circularities in elaboration, and to allow
     * handling of specific keys.
     *
     * @param symbolsEditor the user pane
     */
    public void setSymbolsEditor (SymbolsEditor symbolsEditor)
    {
        editor = symbolsEditor;

        NestView view = editor.getView();
        InputMap inputMap = view.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        // Support for delete key
        inputMap.put(KeyStroke.getKeyStroke("DELETE"), "RemoveAction");
        view.getActionMap().put("RemoveAction", new RemoveAction());
    }

    //------------//
    // splitChord //
    //------------//
    /**
     * Split the provided head chord into 2 separate chords.
     * <p>
     * The strategy is to split the heads where inter-head vertical distance is maximum.
     *
     * @param chord the chord to be split
     */
    @UIThread
    public void splitChord (final HeadChordInter chord)
    {
        new CtrlTask(DO, "splitChord")
        {
            private final List<HeadChordInter> newChords = new ArrayList<>();

            @Override
            protected void action ()
            {
                final SIGraph sig = chord.getSig();

                // Notes are assumed to be ordered bottom up (byReverseCenterOrdinate)
                final List<List<HeadInter>> partitions = partitionHeads(chord);

                for (List<HeadInter> partition : partitions) {
                    final List<Link> newChordLinks = new ArrayList<>();
                    for (HeadInter head : partition) {
                        newChordLinks.add(new Link(head, new Containment(), true));
                    }

                    // Transfer original chords relations to proper sub-chords
                    //TODO
                    //
                    final Rectangle bounds = Entities.getBounds(partition);
                    final HeadChordInter ch = new HeadChordInter(1.0);
                    ch.setManual(true);
                    ch.setStaff(partition.get(0).getStaff());
                    newChords.add(ch);
                    seq.add(new AdditionTask(sig, ch, bounds, newChordLinks));
                }

                // Unlink each head from the original chord
                for (Relation rel : sig.getRelations(chord, Containment.class)) {
                    seq.add(new UnlinkTask(sig, rel));
                }

                final StemInter stem = chord.getStem();
                final Point tail = chord.getTailLocation();
                final int yDir = Integer.compare(tail.y, chord.getCenter().y);

                // Remove the original chord (before dealing with beams)
                seq.add(new RemovalTask(chord));

                // Case of stem-based chord
                if (stem != null) {
                    final Rectangle[] boxes = getSubStemsBounds(stem, tail, yDir, partitions);

                    for (int i = 0; i < 2; i++) {
                        final List<HeadInter> partition = partitions.get(i);

                        // Create stem
                        StemInter s = new StemInter(null, 1.0);
                        s.setManual(true);

                        final List<Link> newStemLinks = new ArrayList<>();
                        for (HeadInter head : partition) {
                            newStemLinks.add(new Link(head, new HeadStemRelation(), false));
                        }

                        // Transfer original stem relations (beams, flags) to proper sub-stem
                        if ((yDir == -1 && i == 1) || (yDir == 1 && i == 0)) {
                            for (Relation rel : sig.getRelations(stem, BeamStemRelation.class,
                                                                 FlagStemRelation.class)) {
                                Inter target = sig.getEdgeTarget(rel);
                                Inter other = sig.getOppositeInter(stem, rel);
                                Relation dup = rel.duplicate();
                                newStemLinks.add(new Link(other, dup, other == target));
                            }
                        }

                        seq.add(new AdditionTask(sig, s, boxes[i], newStemLinks));
                    }

                    // Remove the original stem
                    seq.add(new RemovalTask(stem));
                }

                logger.debug("Split {}", seq);
            }

            @Override
            protected void publish ()
            {
                for (HeadChordInter ch : newChords) {
                    ch.countDots();
                }

                sheet.getInterIndex().publish(null); // TODO: publish both parts?
            }
        }.execute();
    }

    //-------------------//
    // getSubStemsBounds //
    //-------------------//
    /**
     * Compute the box for each of the 2 sub-stems that result from chord split.
     *
     * @param stem       the original chord stem
     * @param tail       the chord tail point
     * @param yDir       stem direction
     * @param partitions the 2 detected head partitions (bottom up)
     * @return the bounds for each sub-stem (bottom up)
     */
    private Rectangle[] getSubStemsBounds (StemInter stem,
                                           Point tail,
                                           int yDir,
                                           List<List<HeadInter>> partitions)
    {
        final Rectangle[] boundsArray = new Rectangle[2];
        final Line2D median = stem.getMedian();
        final int width = sheet.getScale().getStemThickness();

        for (int i = 0; i < 2; i++) {
            final List<HeadInter> p = partitions.get(i);
            final int stemTop;
            final int stemBottom;

            if (i == 0) {
                // Process bottom partition
                if (yDir < 0) {
                    // Stem going up
                    final List<HeadInter> p1 = partitions.get(1); // Other (top) partition
                    stemTop = p1.get(0).getCenter().y;
                    stemBottom = p.get(0).getCenter().y;
                } else {
                    // Stem going down
                    stemTop = p.get(p.size() - 1).getCenter().y;
                    stemBottom = tail.y;
                }
            } else {
                // Process top partition
                if (yDir < 0) {
                    // Stem going up
                    stemTop = tail.y;
                    stemBottom = p.get(0).getCenter().y;
                } else {
                    // Stem going down
                    final List<HeadInter> p0 = partitions.get(0); // Other (bottom) partition
                    stemTop = p.get(p.size() - 1).getCenter().y;
                    stemBottom = p0.get(p0.size() - 1).getCenter().y;
                }
            }

            final Point top = PointUtil.rounded(LineUtil.intersectionAtY(median, stemTop));
            final Point bottom = PointUtil.rounded(LineUtil.intersectionAtY(median, stemBottom));
            final Area area = AreaUtil.verticalParallelogram(top, bottom, width);
            boundsArray[i] = area.getBounds();
        }

        return boundsArray;
    }

    //----------------//
    // partitionHeads //
    //----------------//
    /**
     * Partition the heads of provided chord into 2 partitions.
     *
     * @param chord the provided chord
     * @return the sequence of 2 head partitions
     */
    private List<List<HeadInter>> partitionHeads (HeadChordInter chord)
    {
        final List<? extends Inter> notes = chord.getNotes();

        Point prevCenter = null;
        Integer maxDy = null;
        int bestIndex = 0;

        for (int i = 0; i < notes.size(); i++) {
            HeadInter head = (HeadInter) notes.get(i);
            Point center = head.getCenter();

            if (prevCenter != null) {
                int dy = prevCenter.y - center.y;

                if (maxDy == null || maxDy < dy) {
                    maxDy = dy;
                    bestIndex = i;
                }
            }

            prevCenter = center;
        }

        // We decide to split at bestIndex
        final List<List<HeadInter>> lists = new ArrayList<>();

        List<HeadInter> one = new ArrayList<>();
        for (Inter inter : notes.subList(0, bestIndex)) {
            one.add((HeadInter) inter);
        }

        List<HeadInter> two = new ArrayList<>();
        for (Inter inter : notes.subList(bestIndex, notes.size())) {
            two.add((HeadInter) inter);
        }

        lists.add(one);
        lists.add(two);

        return lists;
    }

    //------//
    // undo //
    //------//
    /**
     * Undo last user action.
     */
    @UIThread
    public void undo ()
    {
        new CtrlTask(UNDO, "undo")
        {
            @Override
            protected void action ()
            {
                seq = history.toUndo();
            }

            @Override
            protected void publish ()
            {
                sheet.getInterIndex().publish(null);
            }
        }.execute();
    }

    //--------//
    // unlink //
    //--------//
    /**
     * Remove a relation between inters.
     *
     * @param sig      the containing SIG
     * @param relation the relation to remove
     */
    @UIThread
    public void unlink (final SIGraph sig,
                        final Relation relation)
    {
        new CtrlTask(DO, "unlink")
        {
            private Inter source = null;

            @Override
            protected void action ()
            {
                seq.add(new UnlinkTask(sig, relation));
                source = sig.getEdgeSource(relation);
            }

            @Override
            protected void publish ()
            {
                sheet.getInterIndex().publish(source);
            }
        }.execute();
    }

    //-----------//
    // addGhosts //
    //-----------//
    /**
     * Perform ghosts addition.
     * It completes {@link #addInters}.
     *
     * @param seq          (output) the UITaskList to populate
     * @param linkedGhosts the ghost inters to add/drop with their links
     */
    private void addGhosts (UITaskList seq,
                            List<LinkedGhost> linkedGhosts)
    {
        for (LinkedGhost linkedGhost : linkedGhosts) {
            final Inter ghost = linkedGhost.ghost;
            final Collection<Link> links = linkedGhost.links;
            final Rectangle ghostBounds = ghost.getBounds();
            final Staff staff = ghost.getStaff();
            final SystemInfo system = staff.getSystem();
            final SIGraph sig = system.getSig();

            // Inter addition
            seq.add(new AdditionTask(sig, ghost, ghostBounds, links));
            sheet.getSymbolsEditor().getShapeBoard().addToHistory(ghost.getShape());

            // Related additions if any
            if (ghost instanceof RestInter) {
                // Wrap this rest within a rest chord
                RestChordInter restChord = new RestChordInter(-1);
                restChord.setStaff(staff);
                seq.add(
                        new AdditionTask(
                                sig,
                                restChord,
                                ghostBounds,
                                Arrays.asList(new Link(ghost, new Containment(), true))));
            } else if (ghost instanceof HeadInter) {
                // If we link head to a stem, create/update the related head chord
                boolean stemFound = false;

                for (Link link : links) {
                    if (link.relation instanceof HeadStemRelation) {
                        final StemInter stem = (StemInter) link.partner;
                        final HeadChordInter headChord;
                        final List<HeadChordInter> stemChords = stem.getChords();

                        if (stemChords.isEmpty()) {
                            // Create a chord based on stem
                            headChord = new HeadChordInter(-1);
                            seq.add(
                                    new AdditionTask(
                                            sig,
                                            headChord,
                                            stem.getBounds(),
                                            Collections.EMPTY_SET));
                            seq.add(new LinkTask(sig, headChord, stem, new ChordStemRelation()));
                        } else {
                            if (stemChords.size() > 1) {
                                logger.warn("Stem shared by several chords, picked one");
                            }

                            headChord = stemChords.get(0);
                        }

                        // Declare head part of head-chord
                        seq.add(new LinkTask(sig, headChord, ghost, new Containment()));
                        stemFound = true;

                        break;
                    }
                }

                if (!stemFound) {
                    // Head without stem
                    HeadChordInter headChord = new HeadChordInter(-1);
                    seq.add(new AdditionTask(sig, headChord, ghostBounds, Collections.EMPTY_SET));
                    seq.add(new LinkTask(sig, headChord, ghost, new Containment()));
                }
            }
        }
    }

    //---------//
    // addText //
    //---------//
    /**
     * Special addition of glyph text
     *
     * @param glyph to be OCR'ed to text lines and words
     * @param shape either TEXT or LYRICS
     */
    @UIThread
    private void addText (final Glyph glyph,
                          final Shape shape)
    {
        new CtrlTask(DO, "addText")
        {
            @Override
            protected void action ()
            {
                if (!OcrUtil.getOcr().isAvailable()) {
                    logger.info(OCR.NO_OCR);

                    return;
                }

                final Point centroid = glyph.getCentroid();
                final SystemInfo system = sheet.getSystemManager().getClosestSystem(centroid);

                if (system == null) {
                    return;
                }

                final SIGraph sig = system.getSig();

                // Retrieve lines relative to glyph origin
                ByteProcessor buffer = glyph.getBuffer();
                List<TextLine> relativeLines = new BlockScanner(sheet).scanBuffer(
                        buffer,
                        sheet.getStub().getOcrLanguages().getValue(),
                        glyph.getId());

                // Retrieve absolute lines (and the underlying word glyphs)
                boolean lyrics = shape == Shape.LYRICS;
                List<TextLine> lines = new TextBuilder(system, lyrics).retrieveGlyphLines(
                        buffer,
                        relativeLines,
                        glyph.getTopLeft());

                // Generate the sequence of word/line Inter additions
                for (TextLine line : lines) {
                    logger.debug("line {}", line);

                    TextRole role = line.getRole();
                    SentenceInter sentence = null;
                    Staff staff = null;

                    for (TextWord textWord : line.getWords()) {
                        logger.debug("word {}", textWord);

                        WordInter word = lyrics ? new LyricItemInter(textWord)
                                : new WordInter(textWord);

                        if (sentence != null) {
                            seq.add(
                                    new AdditionTask(
                                            sig,
                                            word,
                                            textWord.getBounds(),
                                            Arrays.asList(
                                                    new Link(
                                                            sentence,
                                                            new Containment(),
                                                            false))));
                        } else {
                            sentence = lyrics ? LyricLineInter.create(line)
                                    : ((role == TextRole.ChordName) ? ChordNameInter.create(
                                                    line) : SentenceInter.create(line));
                            staff = sentence.assignStaff(system, line.getLocation());
                            seq.add(
                                    new AdditionTask(
                                            sig,
                                            word,
                                            textWord.getBounds(),
                                            Collections.EMPTY_SET));
                            seq.add(
                                    new AdditionTask(
                                            sig,
                                            sentence,
                                            line.getBounds(),
                                            Arrays.asList(
                                                    new Link(word, new Containment(), true))));
                        }

                        word.setStaff(staff);
                    }
                }
            }

            @Override
            protected void publish ()
            {
                sheet.getInterIndex().publish(null);
                sheet.getGlyphIndex().publish(null);
                sheet.getSymbolsEditor().getShapeBoard().addToHistory(shape);
            }
        }.execute();
    }

    //------------//
    // barlinesOf //
    //------------//
    private List<Inter> barlinesOf (Collection<? extends Inter> inters)
    {
        return Inters.inters(inters, new Inters.ClassPredicate(BarlineInter.class));
    }

    //--------------------------//
    // buildStaffBarlineClosure //
    //--------------------------//
    private List<Inter> buildStaffBarlineClosure (StaffBarlineInter oneBar)
    {
        final Rectangle oneBounds = oneBar.getBounds();
        final Staff staff = oneBar.getStaff();
        final SystemInfo system = staff.getSystem();

        // Include a staffBarline per system staff, properly positioned in abscissa
        final Skew skew = sheet.getSkew();
        final Point center = GeoUtil.centerOf(oneBounds);
        final double slope = skew.getSlope();
        final List<Inter> closure = new ArrayList<>();

        for (Staff st : system.getStaves()) {
            if (st == staff) {
                closure.add(oneBar);
            } else {
                double y1 = st.getFirstLine().yAt(center.getX());
                double y2 = st.getLastLine().yAt(center.getX());
                double y = (y1 + y2) / 2;
                double x = center.x - ((y - center.y) * slope);
                Rectangle box = new Rectangle((int) Math.rint(x), (int) Math.rint(y), 0, 0);
                box.grow(oneBounds.width / 2, oneBounds.height / 2);

                StaffBarlineInter g = new StaffBarlineInter(oneBar.getShape(), 1);
                g.setManual(true);
                g.setStaff(st);
                g.setBounds(box);
                closure.add(g);
            }
        }

        // Display closure staff barlines to user
        sheet.getInterIndex().getEntityService().publish(
                new EntityListEvent<>(
                        this,
                        SelectionHint.ENTITY_INIT,
                        MouseMovement.PRESSING,
                        closure));

        if (OMR.gui.displayConfirmation(
                "Do you confirm whole system-height addition?",
                "Insertion of " + closure.size() + " barline(s)")) {
            return closure;
        } else {
            return Collections.EMPTY_LIST;
        }
    }

    //----------------//
    // determineStaff //
    //----------------//
    /**
     * Determine the target staff for the provided glyph.
     *
     * @param glyph provided glyph
     * @param ghost glyph-based ghost
     * @param links (output) to be populated by links
     * @return the staff found or null
     */
    private Staff determineStaff (Glyph glyph,
                                  Inter ghost,
                                  Collection<Link> links)
    {
        Staff staff = null;
        SystemInfo system;
        final Point center = glyph.getCenter();
        final List<Staff> staves = sheet.getStaffManager().getStavesOf(center);

        if (staves.isEmpty()) {
            throw new IllegalStateException("No staff for " + center);
        }

        if ((staves.size() == 1) || ghost instanceof BarlineInter
                    || ghost instanceof StaffBarlineInter) {
            // Staff is uniquely defined
            staff = staves.get(0);
            system = staff.getSystem();
            links.addAll(ghost.searchLinks(system, false));

            return staff;
        }

        // Sort the 2 staves by increasing distance from glyph center
        Collections.sort(staves, new Comparator<Staff>()
                 {
                     @Override
                     public int compare (Staff s1,
                                         Staff s2)
                     {
                         return Double.compare(s1.distanceTo(center), s2.distanceTo(center));
                     }
                 });

        if (constants.useStaffLink.isSet()) {
            // Try to use link
            SystemInfo prevSystem = null;
            StaffLoop:
            for (Staff stf : staves) {
                system = stf.getSystem();

                if (system != prevSystem) {
                    links.addAll(ghost.searchLinks(system, false));

                    for (Link p : links) {
                        if (p.partner.getStaff() != null) {
                            staff = p.partner.getStaff();

                            // We stop on first link found (we check closest staff first)
                            break StaffLoop;
                        }
                    }

                    links.clear();
                }

                prevSystem = system;
            }
        }

        if ((staff == null) && constants.useStaffProximity.isSet()) {
            // Use proximity to staff (vertical margin defined as ratio of gutter)
            final double bestDist = staves.get(0).distanceTo(center);
            final double otherDist = staves.get(1).distanceTo(center);
            final double gutter = bestDist + otherDist;

            if (bestDist <= (gutter * constants.gutterRatio.getValue())) {
                staff = staves.get(0);
            }
        }

        if (staff == null) {
            // Finally, prompt user...
            int option = StaffSelection.getInstance().prompt();

            if (option >= 0) {
                staff = staves.get(option);
            }
        }

        return staff;
    }

    //------------------------//
    // getStaffBarlineClosure //
    //------------------------//
    private List<Inter> getStaffBarlineClosure (StaffBarlineInter oneBar)
    {
        final List<Inter> closure = new ArrayList<>();

        for (PartBarline pb : oneBar.getSystemBarline()) {
            closure.addAll(pb.getStaffBarlines());
        }

        // Display closure staff barlines to user
        sheet.getInterIndex().getEntityService().publish(
                new EntityListEvent<>(
                        this,
                        SelectionHint.ENTITY_INIT,
                        MouseMovement.PRESSING,
                        closure));

        if (OMR.gui.displayConfirmation(
                "Do you confirm whole system-height removal?",
                "Removal of " + closure.size() + " barline(s)")) {
            return closure;
        } else {
            return Collections.EMPTY_LIST;
        }
    }

    //------------------//
    // populateRemovals //
    //------------------//
    /**
     * Prepare removal of the provided inters (with their relations)
     *
     * @param inters the inters to remove
     * @param seq    the task sequence to append to
     */
    private void populateRemovals (Collection<? extends Inter> inters,
                                   UITaskList seq)
    {
        // Dry run
        final Removal removal = new Removal();

        for (Inter inter : inters) {
            if (inter.isRemoved()) {
                continue;
            }

            if (inter.isVip()) {
                logger.info("VIP removeInter {}", inter);
            }

            removal.include(inter);
        }

        // Now set the removal tasks
        removal.populateTaskList(seq);
    }

    //-----------------//
    // preHeadStemLink //
    //-----------------//
    /**
     * Specific actions before linking a head and a stem.
     * <ul>
     * <li>If head is not yet part of a chord, no specific preparation is needed.
     * <li>If the link is to result in a canonical share (down stem + head + up stem) then the
     * head must be "mirrored" between left and right chords.
     * <li>Otherwise, if link is to result in a non compatible configuration between head, stem
     * and existing chords, then the head must migrate away from its chord to incoming stem chord.
     * </ul>
     *
     * @param seq  action sequence to populate
     * @param head head inter being linked
     * @param stem stem inter being linked
     * @return the (perhaps new) head
     */
    private Inter preHeadStemLink (UITaskList seq,
                                   HeadInter head,
                                   StemInter stem)
    {
        final HeadChordInter headChord = head.getChord(); // Not null
        final SIGraph sig = head.getSig();
        final List<HeadChordInter> stemChords = stem.getChords();
        HeadChordInter stemChord = (!stemChords.isEmpty()) ? stemChords.get(0) : null;

        // Check for a canonical head share, to share head
        final HorizontalSide headSide = (stem.getCenter().x < head.getCenter().x) ? LEFT : RIGHT;
        final StemInter headStem = headChord.getStem();

        final boolean sharing;
        if (headSide == LEFT) {
            sharing = HeadStemRelation.isCanonicalShare(stem, head, headStem);
        } else {
            sharing = HeadStemRelation.isCanonicalShare(headStem, head, stem);
        }

        if (sharing) {
            // Duplicate head and link as mirror
            HeadInter newHead = head.duplicate();
            newHead.setManual(true);
            seq.add(
                    new AdditionTask(
                            sig,
                            newHead,
                            newHead.getBounds(),
                            Arrays.asList(new Link(head, new MirrorRelation(), false))));

            // Insert newHead to stem chord
            if (stemChord == null) {
                stemChord = buildStemChord(seq, stem);
            }

            seq.add(new LinkTask(sig, stemChord, newHead, new Containment()));

            return newHead;
        }

        // If resulting chords are not compatible, move head to stemChord
        if ((stemChords.isEmpty() && (headChord.getStem() != null))
                    || (!stemChords.isEmpty() && !stemChords.contains(headChord))) {
            // Extract head from headChord
            seq.add(new UnlinkTask(sig, sig.getRelation(headChord, head, Containment.class)));

            if (headChord.getNotes().size() <= 1) {
                // Remove headChord getting empty
                seq.add(new RemovalTask(headChord));
            }

            if (stemChord == null) {
                stemChord = buildStemChord(seq, stem);
            }

            // Insert head to stem chord
            seq.add(new LinkTask(sig, stemChord, head, new Containment()));
        }

        return head;
    }

    //----------------//
    // buildStemChord //
    //----------------//
    /**
     * Create a HeadChord on the fly based on provided stem.
     *
     * @param seq  action sequence to populate
     * @param stem the provided stem
     * @return a HeadChord around this stem
     */
    private HeadChordInter buildStemChord (UITaskList seq,
                                           StemInter stem)
    {
        final SIGraph sig = stem.getSig();
        final HeadChordInter stemChord = new HeadChordInter(-1);
        seq.add(new AdditionTask(sig, stemChord, stem.getBounds(), Collections.EMPTY_SET));
        seq.add(new LinkTask(sig, stemChord, stem, new ChordStemRelation()));

        return stemChord;
    }

    //-----------//
    // refreshUI //
    //-----------//
    /**
     * Refresh UI after any user action sequence.
     */
    @UIThread
    private void refreshUI ()
    {
        // Update editor display
        editor.refresh();

        // Update status of undo/redo actions
        final BookActions bookActions = BookActions.getInstance();
        bookActions.setUndoable(canUndo());
        bookActions.setRedoable(canRedo());
    }

    //-------------------//
    // removeCompetitors //
    //-------------------//
    /**
     * Discard any existing Inter with the same underlying glyph.
     *
     * @param glyph  underlying glyph
     * @param system containing system
     */
    private void removeCompetitors (Inter ghost,
                                    Glyph glyph,
                                    SystemInfo system,
                                    UITaskList seq)
    {
        if (glyph == null) {
            return;
        }

        final List<Inter> intersected = system.getSig().intersectedInters(glyph.getBounds());
        final List<Inter> competitors = new ArrayList<>();

        for (Inter inter : intersected) {
            if ((inter != ghost) && (inter.getGlyph() == glyph)) {
                competitors.add(inter);
            }
        }

        populateRemovals(competitors, seq);
    }

    //----------------------------//
    // removeConflictingRelations //
    //----------------------------//
    /**
     * Remove relations that would conflict with the provided to-be-inserted relation.
     *
     * @param seq         the action sequence being worked upon
     * @param sig         the containing SIG
     * @param sourceIsNew true if source has been changed
     * @param source      the actual source (perhaps different from src)
     * @param target      the target provided by user
     * @param relation    the relation to be inserted between source and target
     */
    private void removeConflictingRelations (UITaskList seq,
                                             SIGraph sig,
                                             boolean sourceIsNew,
                                             Inter source,
                                             Inter target,
                                             Relation relation)
    {
        Set<Relation> toRemove = new LinkedHashSet<>();

        if (relation instanceof SlurHeadRelation) {
            // This relation is declared multi-source & multi-target
            // But is single target (head) for each given side
            SlurInter slur = (SlurInter) source;
            HeadInter head = (HeadInter) target;
            HorizontalSide side = (head.getCenter().x < slur.getCenter().x) ? LEFT : RIGHT;
            SlurHeadRelation existingRel = slur.getHeadRelation(side);

            if (existingRel != null) {
                toRemove.add(existingRel);
            }
        }

        // Conflict on sources
        if (relation.isSingleSource()) {
            for (Relation rel : sig.getRelations(target, relation.getClass())) {
                toRemove.add(rel);
            }
        }

        // Conflict on targets
        if (relation.isSingleTarget()) {
            if (!sourceIsNew) {
                for (Relation rel : sig.getRelations(source, relation.getClass())) {
                    toRemove.add(rel);
                }

                // Specific case of (single target) augmentation dot to shared head:
                // We allow a dot source to augment both mirrored head targets
                if (relation instanceof AugmentationRelation && target instanceof HeadInter) {
                    HeadInter mirror = (HeadInter) target.getMirror();

                    if (mirror != null) {
                        Relation mirrorRel = sig.getRelation(source, mirror, relation.getClass());

                        if (mirrorRel != null) {
                            toRemove.remove(mirrorRel);
                        }
                    }
                }
            }
        }

        for (Relation rel : toRemove) {
            seq.add(new UnlinkTask(sig, rel));
        }
    }

    //-----------------//
    // staffBarlinesOf //
    //-----------------//
    private List<Inter> staffBarlinesOf (Collection<? extends Inter> inters)
    {
        return Inters.inters(inters, new Inters.ClassPredicate(StaffBarlineInter.class));
    }

    //-----------//
    // Constants //
    //-----------//
    private static class Constants
            extends ConstantSet
    {

        private final Constant.Boolean useStaffLink = new Constant.Boolean(
                true,
                "Should we use link for staff selection");

        private final Constant.Boolean useStaffProximity = new Constant.Boolean(
                true,
                "Should we use proximity for staff selection");

        private final Constant.Ratio gutterRatio = new Constant.Ratio(
                0.33,
                "Vertical margin as ratio of inter-staff gutter");
    }

    //-------------//
    // LinkedGhost //
    //-------------//
    private static class LinkedGhost
    {

        final Inter ghost;

        final Collection<Link> links;

        public LinkedGhost (Inter ghost,
                            Collection<Link> links)
        {
            this.ghost = ghost;
            this.links = links;
        }

        public LinkedGhost (Inter ghost)
        {
            this(ghost, Collections.EMPTY_LIST);
        }
    }

    //---------//
    // Removal //
    //---------//
    /**
     * Removal scenario used for dry-run before actual operations.
     */
    private static class Removal
    {

        /** Non-ensemble inters to be removed. */
        LinkedHashSet<Inter> inters = new LinkedHashSet<>();

        /** Ensemble inters to be removed. */
        LinkedHashSet<InterEnsemble> ensembles = new LinkedHashSet<>();

        /** Ensemble inters to be watched for potential removal. */
        LinkedHashSet<InterEnsemble> watched = new LinkedHashSet<>();

        public void include (Inter inter)
        {
            if (inter instanceof InterEnsemble) {
                // Include the ensemble and its members
                final InterEnsemble ens = (InterEnsemble) inter;
                ensembles.add(ens);
                inters.addAll(ens.getMembers());

                if (inter instanceof HeadChordInter) {
                    // Remove the chord stem as well
                    final HeadChordInter chord = (HeadChordInter) inter;
                    final StemInter stem = chord.getStem();

                    if (stem != null) {
                        inters.add(stem);
                    }
                }
            } else {
                inters.add(inter);

                // Watch the containing ensemble (if not already to be removed)
                final SIGraph sig = inter.getSig();

                for (Relation rel : sig.getRelations(inter, Containment.class)) {
                    final InterEnsemble ens = (InterEnsemble) sig.getOppositeInter(inter, rel);

                    if (!ensembles.contains(ens)) {
                        watched.add(ens);
                    }
                }
            }
        }

        /**
         * Populate the operational task list
         *
         * @param seq the task list to populate
         */
        public void populateTaskList (UITaskList seq)
        {
            // Examine watched ensembles
            for (InterEnsemble ens : watched) {
                List<Inter> members = new ArrayList<>(ens.getMembers());
                members.removeAll(inters);

                if (members.isEmpty()) {
                    ensembles.add(ens);
                }
            }

            // Ensembles to remove first
            for (InterEnsemble ens : ensembles) {
                seq.add(new RemovalTask(ens));
            }

            // Simple inters to remove second
            for (Inter inter : inters) {
                seq.add(new RemovalTask(inter));
            }
        }

        @Override
        public String toString ()
        {
            StringBuilder sb = new StringBuilder("Removal{");
            sb.append("ensembles:").append(ensembles);
            sb.append(" inters:").append(inters);
            sb.append("}");

            return sb.toString();
        }
    }

    //----------//
    // CtrlTask //
    //----------//
    /**
     * Task class to run user-initiated processing asynchronously.
     */
    private abstract class CtrlTask
            extends VoidTask
    {

        protected final OpKind opKind; // Kind of operation to be performed (DO/UNDO/REDO)

        protected final String opName; // Descriptive name of user action

        protected UITaskList seq = new UITaskList(); // Atomic sequence of tasks

        public CtrlTask (OpKind opKind,
                         String opName,
                         Option... options)
        {
            this.opKind = opKind;
            this.opName = opName;

            seq.setOptions(options);
        }

        @Override
        protected final Void doInBackground ()
        {
            try {
                action(); // 1) Populate task(s) sequence

                // 2) Perform the task(s) sequence
                if (opKind == OpKind.UNDO) {
                    seq.performUndo();
                } else {
                    seq.performDo();
                }

                publish(); // 3) Publications at end of sequence

                epilog(); // 4) Impacted steps
            } catch (Throwable ex) {
                logger.warn("Exception in {} {}", opName, ex.toString(), ex);
            }

            return null;
        }

        /** User background action. */
        protected void action ()
        {
            // Void by default
        }

        /** User background epilog. */
        protected void epilog ()
        {
            if (opKind == OpKind.DO) {
                sheet.getStub().setModified(true);
            }

            // Re-process impacted steps
            final Step latestStep = sheet.getStub().getLatestStep();
            final Step firstStep = firstImpactedStep();
            logger.debug("firstStep: {}", firstStep);

            if ((firstStep != null) && (firstStep.compareTo(latestStep) <= 0)) {
                final EnumSet<Step> steps = EnumSet.range(firstStep, latestStep);

                for (Step step : steps) {
                    logger.debug("Impact {}", step);
                    step.impact(seq, opKind);
                }
            }
        }

        @Override
        @UIThread
        protected void finished ()
        {
            // This method runs on EDT

            // Append to history?
            if ((opKind == DO) && (seq != null) && !seq.isOptionSet(Option.NO_HISTORY)) {
                history.add(seq);
            }

            // Refresh user display
            refreshUI();
        }

        /**
         * Report the first step impacted by the task sequence
         *
         * @return the first impacted step
         */
        protected Step firstImpactedStep ()
        {
            // Classes of inter and relation instances involved
            final Set<Class> classes = new HashSet<>();

            for (UITask task : seq.getTasks()) {
                if (task instanceof InterTask) {
                    InterTask interTask = (InterTask) task;
                    classes.add(interTask.getInter().getClass());
                } else if (task instanceof SystemMergeTask) {
                    classes.add(task.getClass());
                } else if (task instanceof RelationTask) {
                    RelationTask relationTask = (RelationTask) task;
                    classes.add(relationTask.getRelation().getClass());
                }
            }

            for (Step step : Step.values()) {
                for (Class classe : classes) {
                    if (step.isImpactedBy(classe)) {
                        return step; // First step impacted
                    }
                }
            }

            return null; // No impact detected
        }

        /** User background publications at end of task(s) sequence. */
        protected void publish ()
        {
            // Void by default
        }
    }

    //--------------//
    // RemoveAction //
    //--------------//
    /**
     * Action to remove the selected inter. (Bound to DELETE key)
     */
    private class RemoveAction
            extends AbstractAction
    {

        @Override
        public void actionPerformed (ActionEvent e)
        {
            List<Inter> inters = sheet.getInterIndex().getEntityService().getSelectedEntityList();

            if ((inters == null) || inters.isEmpty()) {
                return;
            }

            if ((inters.size() == 1) || OMR.gui.displayConfirmation(
                    "Do you confirm this multiple deletion?",
                    "Deletion of " + inters.size() + " inters")) {
                removeInters(inters);
            }
        }
    }
}
