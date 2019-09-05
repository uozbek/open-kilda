import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ToastrService } from 'ngx-toastr';
import { NgxSpinnerService } from "ngx-spinner";
import { LoaderService } from "../../common/services/loader.service";
import { FormBuilder, FormGroup, FormControl, Validators } from '@angular/forms';
import { SwitchService } from 'src/app/common/services/switch.service';

@Component({
  selector: 'app-networkpath',
  templateUrl: './networkpath.component.html',
  styleUrls: ['./networkpath.component.css']
})
export class NetworkpathComponent implements OnInit {
  networkpathForm: FormGroup;
  switchList:any=[];
  submitted:boolean= false;
  networkPaths:any=[];
  constructor(
    private titleService: Title,
    private toastr: ToastrService,
		private formBuilder:FormBuilder,
    private loaderService: LoaderService,
    private switchService:SwitchService
    ) { }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Network Path');
    this.loadSwitchList();
    this.networkpathForm = this.formBuilder.group({
      source_switch: ['',Validators.required],
      target_switch: ['',Validators.required],
    });
  }

  loadSwitchList(){
    this.loaderService.show('loading switches..')
    this.switchService.getSwitchList().subscribe((data : any) =>{
    
    this.switchList = data;
    this.loaderService.hide();
     },error=>{
       this.loaderService.hide();
       this.toastr.error("No switch data",'Error');
     });
  }

  get f() {
    return this.networkpathForm.controls;
  }

  getNetworkPath(){
    this.submitted = true;
    var self =this;
    if (this.networkpathForm.invalid) {
      return;
    }
    this.loaderService.show('fetching network paths...')
    this.switchService.getNetworkPath(this.networkpathForm.controls['source_switch'].value,this.networkpathForm.controls['target_switch'].value).subscribe(function(paths){
       self.networkPaths = paths.paths;
       console.log('self.networkPaths',self.networkPaths);
       self.loaderService.hide();
    },error=>{
      self.loaderService.hide();
    })
  }
}
