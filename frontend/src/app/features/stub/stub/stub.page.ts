import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';

interface StubData {
  title: string;
  subtitle?: string;
}

@Component({
  selector: 'app-stub-page',
  standalone: true,
  templateUrl: './stub.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StubPage {
  private readonly route = inject(ActivatedRoute);

  protected readonly data = toSignal(
    this.route.data.pipe(map((d) => d as StubData)),
    { initialValue: { title: '' } as StubData },
  );
}
