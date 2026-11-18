import {
    afterNextRender,
    computed,
    ElementRef,
    Injector,
    linkedSignal,
    Signal,
    WritableSignal,
} from '@angular/core';

type TouchChrome = 'portrait' | 'landscape' | null;

export interface HeaderTouchSearchDeps {
    readonly touchChrome: Signal<TouchChrome>;
    readonly searchQuery: Signal<string>;
    readonly toggle: Signal<ElementRef<HTMLButtonElement> | undefined>;
    readonly clearSearch: () => void;
    readonly injector: Injector;
}

/**
 * Fork-only touch chrome (tv-shell touch mode, phone in portrait): the header
 * search is an icon until it is used. The icon expands the field over the
 * header row, and a term keeps it expanded. Escape (the tv-shell's Back sends
 * it to the open Back layer) or the field's arrow clears the term and folds
 * it back. Kept out of the header component, which holds the overflow logic.
 */
export class HeaderTouchSearch {
    /** True in the portrait touch chrome. */
    readonly active: Signal<boolean>;
    /** The field is folded into its header icon. */
    readonly collapsed: Signal<boolean>;
    private readonly expanded: WritableSignal<boolean>;

    constructor(private readonly deps: HeaderTouchSearchDeps) {
        this.active = computed(() => deps.touchChrome() === 'portrait');
        this.expanded = linkedSignal({
            source: deps.touchChrome,
            computation: () => false,
        });
        this.collapsed = computed(
            () => this.active() && !this.expanded() && deps.searchQuery() === ''
        );
    }

    /**
     * Expands a folded field and runs `focus` once it has rendered. Returns
     * false when the field was already showing (the caller focuses it).
     */
    expandThen(focus: () => void): boolean {
        if (!this.collapsed()) {
            return false;
        }
        this.expanded.set(true);
        afterNextRender(focus, { injector: this.deps.injector });
        return true;
    }

    /** The expanded field's arrow: clear the term and collapse. */
    onCollapse(event: MouseEvent): void {
        event.preventDefault();
        event.stopPropagation();
        this.collapse();
    }

    /** Escape: clear and collapse, and keep the page from seeing the key. */
    onEscape(event: Event): void {
        if (!this.active()) {
            return;
        }
        event.preventDefault();
        event.stopPropagation();
        this.collapse();
    }

    /** Leaving an empty expanded field folds it back into the icon. */
    onFocusOut(event: FocusEvent): void {
        const field = event.currentTarget as HTMLElement | null;
        if (
            !this.active() ||
            this.deps.searchQuery() !== '' ||
            field?.contains(event.relatedTarget as Node | null)
        ) {
            return;
        }
        this.expanded.set(false);
    }

    private collapse(): void {
        if (this.deps.searchQuery() !== '') {
            this.deps.clearSearch();
        }
        this.expanded.set(false);
        afterNextRender(() => this.deps.toggle()?.nativeElement.focus(), {
            injector: this.deps.injector,
        });
    }
}
