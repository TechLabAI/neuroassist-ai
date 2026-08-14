import { bootstrapApplication } from '@angular/platform-browser';
import { AppShellRootComponent } from './app/app-shell-root.component';
import { appConfig } from './app/app.config';

bootstrapApplication(AppShellRootComponent, appConfig).catch((err) =>
  console.error(err),
);
