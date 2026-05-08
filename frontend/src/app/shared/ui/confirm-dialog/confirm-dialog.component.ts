import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { NgClass } from '@angular/common';

import { ConfirmService } from '@core/ui/confirm.service';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [NgClass],
  templateUrl: './confirm-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialogComponent {
  private readonly service = inject(ConfirmService);

  readonly state = this.service.state;

  /** Whether the icon circle should use the danger color (any *-danger variant). */
  readonly isDanger = computed(() => {
    const v = this.state()?.options.variant;
    return v === 'danger' || v === 'soft-danger';
  });

  readonly primaryClasses = computed(() =>
    this.classesFor(this.state()?.options.variant),
  );
  readonly secondaryClasses = computed(() =>
    this.classesFor(this.state()?.options.secondaryVariant),
  );

  private classesFor(variant: string | undefined): string {
    switch (variant) {
      case 'danger':
        return 'bg-danger hover:bg-danger/90 text-white';
      case 'soft-danger':
        return 'bg-transparent border border-danger/40 text-danger hover:bg-danger/10';
      default:
        return 'bg-primary hover:bg-primary-hover text-on-primary';
    }
  }

  private backdropMouseDown = false;

  protected onBackdropMouseDown(event: MouseEvent): void {
    this.backdropMouseDown = event.target === event.currentTarget;
  }

  protected onBackdropClick(event: MouseEvent): void {
    if (!this.backdropMouseDown) return;
    if (event.target !== event.currentTarget) return;
    this.service.cancel();
  }

  protected onConfirm(): void {
    this.service.accept();
  }

  protected onSecondary(): void {
    this.service.acceptSecondary();
  }

  protected onCancel(): void {
    this.service.cancel();
  }
}
