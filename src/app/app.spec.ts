import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('should create the app and load recent requests', () => {
    const fixture = TestBed.createComponent(App);
    const httpMock = TestBed.inject(HttpTestingController);

    httpMock.expectOne('/api/loan-requests/recent').flush([]);
    fixture.detectChanges();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the new request form', () => {
    const fixture = TestBed.createComponent(App);
    const httpMock = TestBed.inject(HttpTestingController);

    httpMock.expectOne('/api/loan-requests/recent').flush([]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Loan Core');
    expect(compiled.querySelectorAll('form input').length).toBe(4);
  });
});
