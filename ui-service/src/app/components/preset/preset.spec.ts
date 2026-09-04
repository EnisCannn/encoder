import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PresetComponent } from './preset';

describe('PresetComponent', () => {
  let component: PresetComponent;
  let fixture: ComponentFixture<PresetComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PresetComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(PresetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
