import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { DeploymentService } from '../../../core/services/deployment.service';
import { ApprovalInbox } from './approval-inbox';

describe('ApprovalInbox actions', () => {
  let fixture: ComponentFixture<ApprovalInbox>;
  let component: ApprovalInbox;
  let deployments: {
    getPendingApprovals: ReturnType<typeof vi.fn>;
    getById: ReturnType<typeof vi.fn>;
    approve: ReturnType<typeof vi.fn>;
    reject: ReturnType<typeof vi.fn>;
  };

  const pending = {
    id: 'd1',
    projectId: 'p1',
    projectName: 'Demo',
    environmentId: 'e1',
    environment: 'staging',
    revisionId: 'r1',
    checksum: 'abc',
    artifactDigest: 'def',
    status: 'READY_FOR_APPROVAL',
    requesterIdentity: 'dev',
    createdAt: new Date().toISOString(),
    approvalCount: 0,
    requiresSecondApprover: false,
    configYaml: 'environment: staging\nvariables:\n  APP_NAME: demo\n',
    variables: [{ name: 'APP_NAME', value: 'demo', secretRef: false }],
    diff: { addedVariables: ['APP_NAME'], removedVariables: [], changedVariables: [] },
  };

  beforeEach(async () => {
    deployments = {
      getPendingApprovals: vi.fn().mockReturnValue(of([pending])),
      getById: vi.fn().mockReturnValue(of(pending)),
      approve: vi.fn().mockReturnValue(of({ ...pending, status: 'APPROVED' })),
      reject: vi.fn().mockReturnValue(of({ ...pending, status: 'REJECTED' })),
    };

    await TestBed.configureTestingModule({
      imports: [ApprovalInbox],
      providers: [
        provideRouter([]),
        { provide: DeploymentService, useValue: deployments },
        {
          provide: AuthService,
          useValue: { canApprove: () => true },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ApprovalInbox);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads pending approvals on init', () => {
    expect(deployments.getPendingApprovals).toHaveBeenCalled();
    expect(component.items().length).toBe(1);
  });

  it('approves selected deployment with bound digests', () => {
    component.selected.set(component.items()[0]);
    component.detail.set(pending);
    component.decision.setValue({ comment: 'LGTM' });
    component.approve();
    expect(deployments.approve).toHaveBeenCalledWith('d1', {
      comment: 'LGTM',
      checksum: 'abc',
      artifactDigest: 'def',
    });
  });

  it('rejects selected deployment with bound digests', () => {
    component.selected.set(component.items()[0]);
    component.detail.set(pending);
    component.decision.setValue({ comment: 'No' });
    component.reject();
    expect(deployments.reject).toHaveBeenCalledWith('d1', {
      comment: 'No',
      checksum: 'abc',
      artifactDigest: 'def',
    });
  });
});
